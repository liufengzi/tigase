/*
 * DependencyManager.java
 *
 * Tigase Jabber/XMPP Server
 * Copyright (C) 2004-2016 "Tigase, Inc." <office@tigase.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 */

package tigase.kernel.core;

import tigase.kernel.beans.Inject;
import tigase.kernel.core.BeanConfig.State;
import tigase.util.ReflectionHelper;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DependencyManager {

	protected final Logger log = Logger.getLogger(this.getClass().getName());
	private final Map<String, BeanConfig> beanConfigs = new ConcurrentHashMap<>();
	private DependencyManager parent;
	/**
	 * if <code>true</code> then DependencyManager will throw exception if it
	 * can't create beanConfig. If <code>false</code> then
	 * {@link DependencyManager#createBeanConfig(Kernel, String, Class)} will
	 * return null instead of BeanConfig.
	 */
	private boolean throwExceptionIfCannotCreate = false;

	public static Field[] getAllFields(Class<?> klass) {
		List<Field> fields = new ArrayList<Field>();
		fields.addAll(Arrays.asList(klass.getDeclaredFields()));
		if (klass.getSuperclass() != null) {
			fields.addAll(Arrays.asList(getAllFields(klass.getSuperclass())));
		}
		return fields.toArray(new Field[]{});
	}

	public static boolean match(Dependency dependency, BeanConfig beanConfig) {
		if (dependency.getBeanName() != null) {
			return beanConfig.getBeanName().equals(dependency.getBeanName());
		} else if (dependency.getType() != null) {
			Class<?> type = dependency.getType();
			if (Collection.class.isAssignableFrom(type)) {
				type = ReflectionHelper.getCollectionParamter(dependency.getGenericType(), dependency.getBeanConfig().getClazz());
			}
			return type.isAssignableFrom(beanConfig.getClazz());
		} else
			throw new RuntimeException("Unsupported dependecy type.");
	}

	protected BeanConfig createBeanConfig(final Kernel kernel, final String beanName, final Class<?> beanClass) {
		try {
			BeanConfig result = new BeanConfig(beanName, beanClass);
			result.setKernel(kernel);
			prepareDependencies(result);
			return result;
		} catch (java.lang.NoClassDefFoundError e) {
			log.log(Level.WARNING, "Cannot create bean config '" + beanName + "', type=" + beanClass.getName()
					+ ". Bean requires unknown class " + e.getMessage());

			if (throwExceptionIfCannotCreate) {
				throw e;
			} else {
				return null;
			}
		}
	}

	BeanConfig[] findDelegationTo(final BeanConfig beanConfig) {
		return beanConfigs.values()
				.stream()
				.filter(beanConfig1 -> beanConfig1 instanceof Kernel.DelegatedBeanConfig &&
						((Kernel.DelegatedBeanConfig) beanConfig1).getOriginal().equals(beanConfig))
				.toArray(BeanConfig[]::new);
	}

	private Map<Field, Inject> createFieldsDependencyList(final Class<?> cls) {
		Map<Field, Inject> deps = new HashMap<Field, Inject>();
		for (Field field : getAllFields(cls)) {
			Inject injectAnnotation = field.getAnnotation(Inject.class);
			if (injectAnnotation != null) {
				deps.put(field, injectAnnotation);
			}
		}
		return deps;
	}

	public BeanConfig[] getBeanConfig(Dependency dependency) {
		ArrayList<BeanConfig> bcs = new ArrayList<BeanConfig>();
		if (this.parent != null && this.parent != this) {
			BeanConfig[] pds = this.parent.getBeanConfig(dependency);
			for (BeanConfig beanConfig : pds) {
				if (beanConfig != null && beanConfig.isExportable() && beanConfig.getState() != State.inactive)
					bcs.add(beanConfig);
			}
		}
		if (dependency.getBeanName() != null) {
			BeanConfig b = beanConfigs.get(dependency.getBeanName());
			if (b != null && b.getState() != State.inactive)
				bcs.add(b);
			if (bcs.isEmpty())
				bcs.add(null);
		} else if (dependency.getType() != null) {
			Class<?> type = dependency.getType();
			if (Collection.class.isAssignableFrom(type)) {
				type = ReflectionHelper.getCollectionParamter(dependency.getGenericType(), dependency.getBeanConfig().getClazz());
			}
			// TODO - FIXME
			bcs.addAll(getBeanConfigs(type, dependency.getGenericType(), dependency.getBeanConfig().getClazz()));
		} else
			throw new RuntimeException("Unsupported dependecy type.");
		return bcs.toArray(new BeanConfig[]{});
	}

	public BeanConfig getBeanConfig(String beanName) {
		return this.beanConfigs.get(beanName);
	}

	public Collection<BeanConfig> getBeanConfigs() {
		return Collections.unmodifiableCollection(beanConfigs.values());
	}

	public List<BeanConfig> getBeanConfigs(Class<?> type, Type genericType, Class<?> ownerClass) {
		return getBeanConfigs(type, genericType, ownerClass, true);
	}

	public List<BeanConfig> getBeanConfigs(final Class<?> type, Type genericType, Class<?> ownerClass, final boolean allowNonExportable) {
		ArrayList<BeanConfig> result = new ArrayList<BeanConfig>();
		for (BeanConfig bc : beanConfigs.values()) {
			if (bc.getState() != State.inactive && type.isAssignableFrom(bc.getClazz()) && (allowNonExportable || bc.isExportable())) {
				if (genericType == null) {
					result.add(bc);
					continue;
				}

				if (genericType instanceof Class) {
					if (((Class) genericType).isAssignableFrom(bc.getClazz())) {
						result.add(bc);
						continue;
					}
				}

				if (genericType instanceof ParameterizedType) {
					ParameterizedType pt = (ParameterizedType) genericType;
					Type[] types = pt.getActualTypeArguments();
					types = Arrays.copyOf(types, types.length);

					Map<TypeVariable<?>, Type> map = ReflectionHelper.createGenericsTypeMap(ownerClass);
					for (int i=0; i<types.length; i++) {
						Type t = types[i];
						while (t instanceof TypeVariable && map.containsKey(t)) {
							t = map.get((TypeVariable<?>) t);
						}
						if (t instanceof TypeVariable && ((TypeVariable) t).getBounds() != null && ((TypeVariable) t).getBounds().length > 0) {
							t = ((TypeVariable) t).getBounds()[0];
						}
						types[i] = t;
					}

					if (ReflectionHelper.classMatchesClassWithParameters(bc.getClazz(), type, types)) {
						result.add(bc);
						continue;
					}
				}
//				if (genericType == null || (genericType instanceof ParameterizedType && Collection.class.isAssignableFrom((Class) ((ParameterizedType) genericType).getRawType())) || ReflectionHelper.classMatchesType(bc.getClazz(), genericType))
//					result.add(bc);
			}
		}
		return result;
	}

	public Collection<Dependency> getDependenciesTo(BeanConfig destination) {
		HashSet<Dependency> result = new HashSet<Dependency>();
		for (BeanConfig candidate : beanConfigs.values()) {
			for (Dependency dp : candidate.getFieldDependencies().values()) {
				List<BeanConfig> bcs = Arrays.asList(getBeanConfig(dp));
				if (bcs.contains(destination)) {
					result.add(dp);
				}
			}
		}
		return result;
	}

	public HashSet<BeanConfig> getDependentBeans(final BeanConfig beanConfig) {
		HashSet<BeanConfig> result = new HashSet<BeanConfig>();
		for (BeanConfig candidate : beanConfigs.values()) {
			if (candidate.getState() == State.inactive) continue;
			for (Dependency dp : candidate.getFieldDependencies().values()) {
				List<BeanConfig> bcs = Arrays.asList(getBeanConfig(dp));
				if (bcs.contains(beanConfig)) {
					result.add(candidate);
				}
			}
		}
		return result;
	}

	DependencyManager getParent() {
		return parent;
	}

	void setParent(DependencyManager parent) {
		this.parent = parent;
	}

	public boolean isBeanClassRegistered(String beanName) {
		return beanConfigs.containsKey(beanName);
	}

	public boolean isThrowExceptionIfCannotCreate() {
		return throwExceptionIfCannotCreate;
	}

	public void setThrowExceptionIfCannotCreate(boolean throwExceptionIfCannotCreate) {
		this.throwExceptionIfCannotCreate = throwExceptionIfCannotCreate;
	}

	protected void prepareDependencies(BeanConfig beanConfig) {
		final String id = beanConfig.getBeanName();
		final Class<?> cls = beanConfig.getClazz();

		Map<Field, Inject> deps = createFieldsDependencyList(cls);
		for (Entry<Field, Inject> e : deps.entrySet()) {
			Field f = e.getKey();
			Dependency d = new Dependency(beanConfig);
			d.setField(f);
			d.setNullAllowed(e.getValue().nullAllowed());
			if (!e.getValue().bean().isEmpty()) {
				d.setBeanName(e.getValue().bean());
			} else if (e.getValue().type() != Inject.EMPTY.class) {
				d.setType(e.getValue().type());
			} else if (f.getType().isArray()) {
				d.setType(f.getType().getComponentType());
			} else {
				Class<?> type = f.getType();
				d.setType(type);
				d.setGenericType(f.getGenericType());
			}

			beanConfig.getFieldDependencies().put(e.getKey(), d);
		}
	}

	void register(BeanConfig beanConfig) {
		beanConfigs.put(beanConfig.getBeanName(), beanConfig);
		if (beanConfig.getState() != State.inactive)
			beanConfig.setState(State.registered);
	}

	public BeanConfig unregister(String beanName) {
		return beanConfigs.remove(beanName);
	}

}
