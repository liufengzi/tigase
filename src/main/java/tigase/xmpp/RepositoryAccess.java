/*
 * Tigase Jabber/XMPP Server
 * Copyright (C) 2004-2007 "Artur Hefczyc" <artur.hefczyc@tigase.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 *
 * $Rev$
 * Last modified by $Author$
 * $Date$
 */

package tigase.xmpp;

//~--- non-JDK imports --------------------------------------------------------

import tigase.db.AuthorizationException;
import tigase.db.TigaseDBException;
import tigase.db.UserAuthRepository;
import tigase.db.UserExistsException;
import tigase.db.UserNotFoundException;
import tigase.db.UserRepository;

import tigase.util.TigaseStringprepException;

import tigase.vhosts.VHostItem;

import static tigase.db.NonAuthUserRepository.*;

//~--- JDK imports ------------------------------------------------------------

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

//~--- classes ----------------------------------------------------------------

/**
 * Describe class RepositoryAccess here.
 *
 *
 * Created: Tue Oct 24 10:38:41 2006
 *
 * @author <a href="mailto:artur.hefczyc@tigase.org">Artur Hefczyc</a>
 * @version $Rev$
 */
public abstract class RepositoryAccess {

	/**
	 * Private logger for class instancess.
	 */
	private static final Logger log = Logger.getLogger("tigase.xmpp.RepositoryAccess");
	protected static final String NOT_AUTHORIZED_MSG = "Session has not been yet authorised.";
	protected static final String NO_ACCESS_TO_REP_MSG = "Can not access user repository.";
	private static final String ANONYMOUS_MECH = "ANONYMOUS";

	//~--- fields ---------------------------------------------------------------

	private UserAuthRepository authRepo = null;
	protected VHostItem domain = null;
	private JID domainAsJID = null;

	/**
	 * Handle to user repository - permanent data base for storing user data.
	 */
	private UserRepository repo = null;

//private boolean anon_allowed = false;
	private boolean is_anonymous = false;

	/**
	 * Current authorization state - initialy session i <code>NOT_AUTHORIZED</code>.
	 * It becomes <code>AUTHORIZED</code>
	 */
	private Authorization authState = Authorization.NOT_AUTHORIZED;

	//~--- constructors ---------------------------------------------------------

	/**
	 * Creates a new <code>RepositoryAccess</code> instance.
	 *
	 *
	 * @param rep
	 * @param auth
	 */
	public RepositoryAccess(UserRepository rep, UserAuthRepository auth) {
		repo = rep;
		authRepo = auth;

//  this.anon_allowed = anon_allowed;
	}

	//~--- get methods ----------------------------------------------------------

	/**
	 * Method description
	 *
	 *
	 * @return
	 *
	 * @throws NotAuthorizedException
	 */
	public abstract BareJID getUserId() throws NotAuthorizedException;

	/**
	 * Method description
	 *
	 *
	 * @return
	 *
	 * @throws NotAuthorizedException
	 */
	public abstract String getUserName() throws NotAuthorizedException;

	//~--- methods --------------------------------------------------------------

	protected abstract void login();

	/**
	 * Method description
	 *
	 *
	 * @param subnode
	 * @param key
	 * @param list
	 *
	 * @throws NotAuthorizedException
	 * @throws TigaseDBException
	 */
	public void addDataList(final String subnode, final String key, final String[] list)
			throws NotAuthorizedException, TigaseDBException {
		if (is_anonymous) {
			return;
		}

		if ( !isAuthorized()) {
			throw new NotAuthorizedException(NO_ACCESS_TO_REP_MSG);
		}

		try {
			repo.addDataList(getUserId().toString(), subnode, key, list);
		} catch (UserNotFoundException e) {
			log.log(Level.FINEST, "Problem accessing reposiotry: ", e);

			throw new NotAuthorizedException(NO_ACCESS_TO_REP_MSG, e);

//    } catch (TigaseDBException e) {
//     log.log(Level.SEVERE, "Repository access exception.", e);
		}    // end of try-catch
	}

	/**
	 * Method description
	 *
	 *
	 * @param subnode
	 * @param key
	 * @param list
	 *
	 * @throws NotAuthorizedException
	 * @throws TigaseDBException
	 */
	public void addOfflineDataList(String subnode, String key, String[] list)
			throws NotAuthorizedException, TigaseDBException {
		addDataList(calcNode(OFFLINE_DATA_NODE, subnode), key, list);
	}

	/**
	 * Method description
	 *
	 *
	 * @param subnode
	 * @param key
	 * @param list
	 *
	 * @throws NotAuthorizedException
	 * @throws TigaseDBException
	 */
	public void addPublicDataList(String subnode, String key, String[] list)
			throws NotAuthorizedException, TigaseDBException {
		addDataList(calcNode(PUBLIC_DATA_NODE, subnode), key, list);
	}

	//~--- get methods ----------------------------------------------------------

	/**
	 * Gets the value of authState
	 *
	 * @return the value of authState
	 */
	public final Authorization getAuthState() {
		return this.authState;
	}

	/**
	 * Method description
	 *
	 *
	 * @param xmpp_sessionId
	 *
	 * @return
	 *
	 * @throws NotAuthorizedException
	 * @throws TigaseDBException
	 */
	public String getAuthenticationToken(String xmpp_sessionId)
			throws NotAuthorizedException, TigaseDBException {
		UUID token = UUID.randomUUID();

		setData("tokens", xmpp_sessionId, token.toString());

		return token.toString();
	}

	/**
	 * <code>getData</code> method is a twin sister (brother?) of
	 * <code>setData(String, String, String)</code> method.
	 * It allows you to retrieve data stored with above method. It is data stored
	 * in given node with given key identifier. If there are no data associated
	 * with given key or given node does not exist given <code>def</code> value
	 * is returned.
	 *
	 * @param subnode a <code>String</code> value is path to node where pair
	 * <code>(key, value)</code> are stored.
	 * @param key a <code>String</code> value of key ID for data to retrieve.
	 * @param def a <code>String</code> value of default returned if there is
	 * nothing stored with given key. <code>def</code> can be set to any value
	 * you wish to have back as default value or <code>null</code> if you want
	 * to have back <code>null</code> if no data was found. If you set
	 * <code>def</code> to <code>null</code> it has exactly the
	 * same effect as if you use <code>getData(String)</code> method.
	 * @return a <code>String</code> value of data found for given key or
	 * <code>def</code> if there was no data associated with given key.
	 * @exception NotAuthorizedException is thrown when session
	 * has not been authorized yet and there is no access to permanent storage.
	 * @throws TigaseDBException
	 * @see #setData(String, String, String)
	 */
	public String getData(String subnode, String key, String def)
			throws NotAuthorizedException, TigaseDBException {
		if (is_anonymous) {
			return null;
		}

		if ( !isAuthorized()) {
			throw new NotAuthorizedException(NO_ACCESS_TO_REP_MSG);
		}

		try {
			return repo.getData(getUserId().toString(), subnode, key, def);
		} catch (UserNotFoundException e) {
			log.log(Level.FINEST, "Problem accessing reposiotry: ", e);

			throw new NotAuthorizedException(NO_ACCESS_TO_REP_MSG, e);

//    } catch (TigaseDBException e) {
//     log.log(Level.SEVERE, "Repository access exception.", e);
		}    // end of try-catch

//  return null;
	}

	/**
	 * This method retrieves list of all direct subnodes for given node.
	 * It works in similar way as <code>ls</code> unix command or <code>dir</code>
	 * under DOS/Windows systems.
	 *
	 * @param subnode a <code>String</code> value of path to node for which we
	 * want to retrieve list of direct subnodes.
	 * @return a <code>String[]</code> array of direct subnodes names for given
	 * node.
	 * @exception NotAuthorizedException is thrown when session
	 * has not been authorized yet and there is no access to permanent storage.
	 * @throws TigaseDBException
	 * @see #setData(String, String, String)
	 */
	public String[] getDataGroups(String subnode)
			throws NotAuthorizedException, TigaseDBException {
		if (is_anonymous) {
			return null;
		}

		if ( !isAuthorized()) {
			throw new NotAuthorizedException(NO_ACCESS_TO_REP_MSG);
		}

		try {
			return repo.getSubnodes(getUserId().toString(), subnode);
		} catch (UserNotFoundException e) {
			log.log(Level.FINEST, "Problem accessing reposiotry: ", e);

			throw new NotAuthorizedException(NO_ACCESS_TO_REP_MSG, e);

//    } catch (TigaseDBException e) {
//     log.log(Level.SEVERE, "Repository access exception.", e);
		}    // end of try-catch

//  return null;
	}

	/**
	 * This method returns all data keys available in permanent storage in given
	 * node.
	 * There is not though any information what kind of data is stored with this
	 * key. This is up to user (developer) to determine what data type is
	 * associated with key and what is it's meaning.
	 *
	 * @param subnode a <code>String</code> value pointing to specific subnode in
	 * user reposiotry where data have to be stored.
	 * @return a <code>String[]</code> array containing all data keys found in
	 * given subnode.
	 * @exception NotAuthorizedException is thrown when session
	 * has not been authorized yet and there is no access to permanent storage.
	 * @throws TigaseDBException
	 * @see #setData(String, String, String)
	 */
	public String[] getDataKeys(final String subnode)
			throws NotAuthorizedException, TigaseDBException {
		if (is_anonymous) {
			return null;
		}

		if ( !isAuthorized()) {
			throw new NotAuthorizedException(NO_ACCESS_TO_REP_MSG);
		}

		try {
			return repo.getKeys(getUserId().toString(), subnode);
		} catch (UserNotFoundException e) {
			log.log(Level.FINEST, "Problem accessing reposiotry: ", e);

			throw new NotAuthorizedException(NO_ACCESS_TO_REP_MSG, e);

//    } catch (TigaseDBException e) {
//     log.log(Level.SEVERE, "Repository access exception.", e);
		}    // end of try-catch

//  return null;
	}

	/**
	 * This method allows to retrieve list of values associated with one key.
	 * As it is possible to store many values with one key there are a few methods
	 * which provides this functionality. If given key does not exists in given
	 * subnode <code>null</code> is returned.
	 *
	 * @param subnode a <code>String</code> value pointing to specific subnode in
	 * user reposiotry where data have to be stored.
	 * @param key a <code>String</code> value of data key ID.
	 * @return a <code>String[]</code> array containing all values found for
	 * given key.
	 * @exception NotAuthorizedException is thrown when session
	 * has not been authorized yet and there is no access to permanent storage.
	 * @throws TigaseDBException
	 * @see #setData(String, String, String)
	 */
	public String[] getDataList(String subnode, String key)
			throws NotAuthorizedException, TigaseDBException {
		if (is_anonymous) {
			return null;
		}

		if ( !isAuthorized()) {
			throw new NotAuthorizedException(NO_ACCESS_TO_REP_MSG);
		}

		try {
			return repo.getDataList(getUserId().toString(), subnode, key);
		} catch (UserNotFoundException e) {
			log.log(Level.FINEST, "Problem accessing reposiotry: ", e);

			throw new NotAuthorizedException(NO_ACCESS_TO_REP_MSG, e);

//    } catch (TigaseDBException e) {
//     log.log(Level.SEVERE, "Repository access exception.", e);
		}    // end of try-catch

//  return null;
	}

	/**
	 * Method description
	 *
	 *
	 * @return
	 */
	public String getDomain() {
		return domain.getVhost();
	}

	/**
	 * Method description
	 *
	 *
	 * @return
	 */
	public JID getDomainAsJID() {
		return domainAsJID;
	}

	/**
	 * Method description
	 *
	 *
	 * @param subnode
	 * @param key
	 * @param def
	 *
	 * @return
	 *
	 * @throws NotAuthorizedException
	 * @throws TigaseDBException
	 */
	public String getOfflineData(String subnode, String key, String def)
			throws NotAuthorizedException, TigaseDBException {
		return getData(calcNode(OFFLINE_DATA_NODE, subnode), key, def);
	}

	/**
	 * Method description
	 *
	 *
	 * @param subnode
	 * @param key
	 *
	 * @return
	 *
	 * @throws NotAuthorizedException
	 * @throws TigaseDBException
	 */
	public String[] getOfflineDataList(String subnode, String key)
			throws NotAuthorizedException, TigaseDBException {
		return getDataList(calcNode(OFFLINE_DATA_NODE, subnode), key);
	}

	/**
	 * Method description
	 *
	 *
	 * @param subnode
	 * @param key
	 * @param def
	 *
	 * @return
	 *
	 * @throws NotAuthorizedException
	 * @throws TigaseDBException
	 */
	public String getPublicData(String subnode, String key, String def)
			throws NotAuthorizedException, TigaseDBException {
		return getData(calcNode(PUBLIC_DATA_NODE, subnode), key, def);
	}

	/**
	 * Method description
	 *
	 *
	 * @param subnode
	 * @param key
	 *
	 * @return
	 *
	 * @throws NotAuthorizedException
	 * @throws TigaseDBException
	 */
	public String[] getPublicDataList(String subnode, String key)
			throws NotAuthorizedException, TigaseDBException {
		return getDataList(calcNode(PUBLIC_DATA_NODE, subnode), key);
	}

	/**
	 * Method description
	 *
	 *
	 * @return
	 */
	public boolean isAnonymous() {
		return is_anonymous;
	}

///**
// * Sets the value of authState
// *
// * @param argAuthState Value to assign to this.authState
// */
//protected void setAuthState(final Authorization argAuthState) {
//  this.authState = argAuthState;
//}

	/**
	 * This method allows you test this session if it already has been authorized.
	 * If <code>true</code> is returned as method result it means session has
	 * already been authorized, if <code>false</code> however session is still not
	 * authorized.
	 *
	 * @return a <code>boolean</code> value which informs whether this session has
	 * been already authorized or not.
	 */
	public boolean isAuthorized() {
		return authState == Authorization.AUTHORIZED;
	}

	//~--- methods --------------------------------------------------------------

	/**
	 * Method description
	 *
	 *
	 * @param user
	 * @param digest
	 * @param id
	 * @param alg
	 *
	 * @return
	 *
	 * @throws AuthorizationException
	 * @throws NotAuthorizedException
	 * @throws TigaseDBException
	 */
	public Authorization loginDigest(String user, String digest, String id, String alg)
			throws NotAuthorizedException, AuthorizationException, TigaseDBException {
		try {
			if (authRepo.digestAuth(BareJID.toString(user, getDomain()), digest, id, alg)) {
				authState = Authorization.AUTHORIZED;
				login();
			}    // end of if (authRepo.loginPlain())auth.login();

			return authState;
		} catch (UserNotFoundException e) {
			log.log(Level.FINEST, "Problem accessing reposiotry: ", e);

			throw new NotAuthorizedException("Authorization failed", e);

//    } catch (TigaseDBException e) {
//     log.log(Level.SEVERE, "Repository access exception.", e);
//      throw new NotAuthorizedException("Authorization failed", e);
		}      // end of try-catch
	}

	/**
	 * Method description
	 *
	 *
	 * @param props
	 *
	 * @return
	 *
	 * @throws AuthorizationException
	 * @throws NotAuthorizedException
	 * @throws TigaseDBException
	 */
	public Authorization loginOther(Map<String, Object> props)
			throws NotAuthorizedException, AuthorizationException, TigaseDBException {
		try {
			String mech = (String) props.get(UserAuthRepository.MACHANISM_KEY);

			if (domain.isAnonymousEnabled() && (mech != null) && mech.equals(ANONYMOUS_MECH)) {
				is_anonymous = true;
				props.put(UserAuthRepository.USER_ID_KEY, UUID.randomUUID().toString());
				authState = Authorization.AUTHORIZED;
				login();
			} else {
				if (authRepo.otherAuth(props)) {
					authState = Authorization.AUTHORIZED;
					login();
				}    // end of if (authRepo.loginPlain())auth.login();
			}

			return authState;
		} catch (UserNotFoundException e) {
			log.log(Level.FINEST, "Problem accessing reposiotry: ", e);

			throw new NotAuthorizedException("Authorization failed", e);

//    } catch (TigaseDBException e) {
//     log.log(Level.SEVERE, "Repository access exception.", e);
//      throw new NotAuthorizedException("Authorization failed", e);
		}        // end of try-catch
	}

	/**
	 * <code>authorize</code> method performs authorization with given
	 * password as plain text.
	 * If <code>AUTHORIZED</code> has been returned it means authorization
	 * process is successful and session has been activated, otherwise session
	 * hasn't been authorized and return code gives more detailed information
	 * of fail reason. Please refer to <code>Authorizaion</code> documentation for
	 * more details.
	 *
	 * @param user
	 * @param password
	 * @return a <code>Authorization</code> value of result code.
	 * @throws NotAuthorizedException
	 * @throws AuthorizationException
	 * @throws TigaseDBException
	 */
	public Authorization loginPlain(String user, String password)
			throws NotAuthorizedException, AuthorizationException, TigaseDBException {
		try {
			if (authRepo.plainAuth(BareJID.toString(user, getDomain()), password)) {
				authState = Authorization.AUTHORIZED;
				login();
			}    // end of if (authRepo.loginPlain())auth.login();

			return authState;
		} catch (UserNotFoundException e) {
			log.info("User not found, authorization failed: " + user);

			throw new NotAuthorizedException("Authorization failed", e);

//    } catch (TigaseDBException e) {
//     log.log(Level.SEVERE, "Repository access exception.", e);
//      throw new NotAuthorizedException("Authorization failed", e);
		}      // end of try-catch
	}

	/**
	 * Method description
	 *
	 *
	 * @param userId
	 * @param xmpp_sessionId
	 * @param token
	 *
	 * @return
	 *
	 * @throws NotAuthorizedException
	 * @throws TigaseDBException
	 */
	public Authorization loginToken(BareJID userId, String xmpp_sessionId, String token)
			throws NotAuthorizedException, TigaseDBException {
		try {
			String db_token = repo.getData(userId.toString(), "tokens", xmpp_sessionId);

			if (token.equals(db_token)) {
				authState = Authorization.AUTHORIZED;
				login();
				repo.removeData(userId.toString(), "tokens", xmpp_sessionId);
			}
		} catch (UserNotFoundException e) {
			log.log(Level.FINEST, "Problem accessing reposiotry: ", e);

			throw new NotAuthorizedException(NO_ACCESS_TO_REP_MSG, e);

//    } catch (TigaseDBException e) {
//     log.log(Level.SEVERE, "Repository access exception.", e);
		}    // end of try-catch

		return authState;
	}

	/**
	 * Method description
	 *
	 *
	 * @throws NotAuthorizedException
	 */
	public void logout() throws NotAuthorizedException {
		authState = Authorization.NOT_AUTHORIZED;
	}

	/**
	 * Method description
	 *
	 *
	 * @param authProps
	 */
	public void queryAuth(Map<String, Object> authProps) {
		authRepo.queryAuth(authProps);

		if (domain.isAnonymousEnabled()
				&& (authProps.get(UserAuthRepository.PROTOCOL_KEY)
					== UserAuthRepository.PROTOCOL_VAL_SASL)) {
			String[] auth_mechs = (String[]) authProps.get(UserAuthRepository.RESULT_KEY);

			auth_mechs = Arrays.copyOf(auth_mechs, auth_mechs.length + 1);
			auth_mechs[auth_mechs.length - 1] = ANONYMOUS_MECH;
			authProps.put(UserAuthRepository.RESULT_KEY, auth_mechs);
		}
	}

	/**
	 * Method description
	 *
	 *
	 * @param name_param
	 * @param pass_param
	 * @param email_param
	 *
	 * @return
	 *
	 * @throws NotAuthorizedException
	 * @throws TigaseDBException
	 */
	public Authorization register(String name_param, String pass_param, String email_param)
			throws NotAuthorizedException, TigaseDBException {

		// Some clients send plain user name and others send
		// jid as user name. Let's resolve this here.
		String user_name = BareJID.parseJID(name_param)[0];

		if ((user_name == null) || user_name.trim().isEmpty()) {
			user_name = name_param;
		}    // end of if (user_mame == null || user_name.equals(""))

		if (isAuthorized()) {
			return changeRegistration(user_name, pass_param, email_param);
		}

		// new user registration, let's check limits...
		if ( !domain.isRegisterEnabled()) {
			throw new NotAuthorizedException("Registration is now allowed for this domain");
		}

		if (domain.getMaxUsersNumber() > 0) {
			long domainUsers = authRepo.getUsersCount(domain.getVhost());

			if (domainUsers >= domain.getMaxUsersNumber()) {
				throw new NotAuthorizedException("Maximum users number for the domain exceeded.");
			}
		}

		if ((user_name == null) || user_name.equals("") || (pass_param == null)
				|| pass_param.equals("")) {
			return Authorization.NOT_ACCEPTABLE;
		}

		try {
			authRepo.addUser(BareJID.toString(user_name, getDomain()), pass_param);
			log.info("User added: " + BareJID.toString(user_name, getDomain()) + ", pass: "
					+ pass_param);
			setRegistration(user_name, pass_param, email_param);
			log.info("Registration data set for: " + BareJID.toString(user_name, getDomain())
					+ ", pass: " + pass_param + ", email: " + email_param);

			return Authorization.AUTHORIZED;
		} catch (UserExistsException e) {
			return Authorization.CONFLICT;
		} catch (TigaseDBException e) {
			log.log(Level.SEVERE, "Repository access exception.", e);

			return Authorization.INTERNAL_SERVER_ERROR;
		}    // end of try-catch
	}

	/**
	 * Method description
	 *
	 *
	 * @param subnode
	 * @param key
	 *
	 * @throws NotAuthorizedException
	 * @throws TigaseDBException
	 */
	public void removeData(String subnode, String key)
			throws NotAuthorizedException, TigaseDBException {
		try {
			repo.removeData(getUserId().toString(), subnode, key);
		} catch (UserNotFoundException e) {
			log.log(Level.FINEST, "Problem accessing reposiotry: ", e);

			throw new NotAuthorizedException(NO_ACCESS_TO_REP_MSG, e);

//    } catch (TigaseDBException e) {
//     log.log(Level.SEVERE, "Repository access exception.", e);
		}    // end of try-catch
	}

	/**
	 * Removes the last data node given in subnode path as parameter to this
	 * method.
	 * All subnodes are moved as well an all data stored as
	 * <code>(key, val)</code> are removed as well. Changes are commited to
	 * repository immediatelly and there is no way to undo this operation so
	 * use it with care.
	 *
	 * @param subnode a <code>String</code> value of path to node which has
	 * to be removed.
	 * @exception NotAuthorizedException is thrown when session
	 * has not been authorized yet and there is no access to permanent storage.
	 * @throws TigaseDBException
	 * @see #setData(String, String, String)
	 */
	public void removeDataGroup(final String subnode)
			throws NotAuthorizedException, TigaseDBException {
		if (is_anonymous) {
			return;
		}

		if ( !isAuthorized()) {
			throw new NotAuthorizedException(NO_ACCESS_TO_REP_MSG);
		}

		try {
			repo.removeSubnode(getUserId().toString(), subnode);
		} catch (UserNotFoundException e) {
			log.log(Level.FINEST, "Problem accessing reposiotry: ", e);

			throw new NotAuthorizedException(NO_ACCESS_TO_REP_MSG, e);

//    } catch (TigaseDBException e) {
//     log.log(Level.SEVERE, "Repository access exception.", e);
		}    // end of try-catch
	}

	/**
	 * Method description
	 *
	 *
	 * @param subnode
	 * @param key
	 *
	 * @throws NotAuthorizedException
	 * @throws TigaseDBException
	 */
	public void removeOfflineData(String subnode, String key)
			throws NotAuthorizedException, TigaseDBException {
		removeData(calcNode(OFFLINE_DATA_NODE, subnode), key);
	}

	/**
	 * Method description
	 *
	 *
	 * @param subnode
	 *
	 * @throws NotAuthorizedException
	 * @throws TigaseDBException
	 */
	public void removeOfflineDataGroup(String subnode)
			throws NotAuthorizedException, TigaseDBException {
		removeDataGroup(calcNode(OFFLINE_DATA_NODE, subnode));
	}

	/**
	 * Method description
	 *
	 *
	 * @param subnode
	 * @param key
	 *
	 * @throws NotAuthorizedException
	 * @throws TigaseDBException
	 */
	public void removePublicData(String subnode, String key)
			throws NotAuthorizedException, TigaseDBException {
		removeData(calcNode(PUBLIC_DATA_NODE, subnode), key);
	}

	/**
	 * Method description
	 *
	 *
	 * @param subnode
	 *
	 * @throws NotAuthorizedException
	 * @throws TigaseDBException
	 */
	public void removePublicDataGroup(String subnode)
			throws NotAuthorizedException, TigaseDBException {
		removeDataGroup(calcNode(PUBLIC_DATA_NODE, subnode));
	}

	//~--- set methods ----------------------------------------------------------

	/**
	 * This method stores given data in permanent storage in given point of
	 * hierarchy of data base.
	 * This method is similar to <code>setData(String, String)</code> and
	 * differs in one additional parameter which point to user data base subnode
	 * where data must be stored. It helps to organize user data in more logical
	 * hierarchy.<br/>
	 * User data is kind of tree where you can store data in each tree node. The
	 * most relevant sample might be structure like typical file system or
	 * XML like or LDAP data base. The first implementation is actually done as
	 * XML file to make it easier test application and deploy simple installation
	 * where there is no more users than 1000.<br/>
	 * To find out more about user repository refer to <code>UserRepository</code>
	 * interface for general info and to <code>XMLRepository</code> for detailed
	 * explanation regarding XML implementation of user repository.
	 * <p>
	 * Thus <code>subnode</code> is kind of path to data node. If you specify
	 * <code>null</code> or empty node data will be stored in root user node.
	 * This has exactly the same effect as you call
	 * <code>setData(String, String)</code>. If you want to store data in
	 * different node you must just specify node path like you do it to directory
	 * on most file systems:
	 * <pre>
	 * /roster
	 * </pre>
	 * Or, if you need access deeper node:
	 * <pre>
	 * /just/like/path/to/file
	 * </pre>
	 * </p>
	 * If given node does not yet exist it will be automaticaly created with all
	 * nodes in given path so there is no need for developer to perform additional
	 * action to create node. There is, however method
	 * <code>removeDataGroup(String)</code> for deleting specified node as nodes
	 * are not automaticaly deleted.
	 *
	 * @param subnode a <code>String</code> value pointing to specific subnode in
	 * user reposiotry where data have to be stored.
	 * @param key a <code>String</code> value of data key ID.
	 * @param value a <code>String</code> actual data stored in user repository.
	 * @exception NotAuthorizedException is thrown when session
	 * has not been authorized yet and there is no access to permanent storage.
	 * @throws TigaseDBException
	 * @see #removeDataGroup(String)
	 * @see UserRepository
	 */
	public void setData(String subnode, String key, String value)
			throws NotAuthorizedException, TigaseDBException {
		try {
			repo.setData(getUserId().toString(), subnode, key, value);
		} catch (UserNotFoundException e) {
			log.log(Level.FINEST, "Problem accessing reposiotry: ", e);

			throw new NotAuthorizedException(NO_ACCESS_TO_REP_MSG, e);

//    } catch (TigaseDBException e) {
//     log.log(Level.SEVERE, "Repository access exception.", e);
		}    // end of try-catch
	}

	/**
	 * This method allows to store list of values under one key ID reference.
	 * It is often necessary to keep set of values which can be refered by one
	 * key. As an example might be list of groups for specific buddy in roster.
	 * There is no actual need to store each group with separate key because
	 * we usually need to acces whole list of groups.
	 *
	 * @param subnode a <code>String</code> value pointing to specific subnode in
	 * user reposiotry where data have to be stored.
	 * @param key a <code>String</code> value of data key ID.
	 * @param list a <code>String[]</code> keeping list of actual data to be
	 * stored in user repository.
	 * @exception NotAuthorizedException is thrown when session
	 * has not been authorized yet and there is no access to permanent storage.
	 * @throws TigaseDBException
	 * @see #setData(String, String, String)
	 */
	public void setDataList(final String subnode, final String key, final String[] list)
			throws NotAuthorizedException, TigaseDBException {
		if (is_anonymous) {
			return;
		}

		if ( !isAuthorized()) {
			throw new NotAuthorizedException(NO_ACCESS_TO_REP_MSG);
		}

		try {
			repo.setDataList(getUserId().toString(), subnode, key, list);
		} catch (UserNotFoundException e) {
			log.log(Level.FINEST, "Problem accessing reposiotry: ", e);

			throw new NotAuthorizedException(NO_ACCESS_TO_REP_MSG, e);

//    } catch (TigaseDBException e) {
//     log.log(Level.SEVERE, "Repository access exception.", e);
		}    // end of try-catch
	}

	/**
	 * Method description
	 *
	 *
	 * @param domain
	 *
	 * @throws TigaseStringprepException
	 */
	public void setDomain(final VHostItem domain) throws TigaseStringprepException {
		this.domain = domain;
		this.domainAsJID = JID.jidInstance(domain.getVhost());
	}

	/**
	 * Method description
	 *
	 *
	 * @param subnode
	 * @param key
	 * @param value
	 *
	 * @throws NotAuthorizedException
	 * @throws TigaseDBException
	 */
	public void setOfflineData(String subnode, String key, String value)
			throws NotAuthorizedException, TigaseDBException {
		setData(calcNode(OFFLINE_DATA_NODE, subnode), key, value);
	}

	/**
	 * Method description
	 *
	 *
	 * @param subnode
	 * @param key
	 * @param list
	 *
	 * @throws NotAuthorizedException
	 * @throws TigaseDBException
	 */
	public void setOfflineDataList(String subnode, String key, String[] list)
			throws NotAuthorizedException, TigaseDBException {
		setDataList(calcNode(OFFLINE_DATA_NODE, subnode), key, list);
	}

	/**
	 * Method description
	 *
	 *
	 * @param subnode
	 * @param key
	 * @param value
	 *
	 * @throws NotAuthorizedException
	 * @throws TigaseDBException
	 */
	public void setPublicData(String subnode, String key, String value)
			throws NotAuthorizedException, TigaseDBException {
		setData(calcNode(PUBLIC_DATA_NODE, subnode), key, value);
	}

	/**
	 * Method description
	 *
	 *
	 * @param subnode
	 * @param key
	 * @param list
	 *
	 * @throws NotAuthorizedException
	 * @throws TigaseDBException
	 */
	public void setPublicDataList(String subnode, String key, String[] list)
			throws NotAuthorizedException, TigaseDBException {
		setDataList(calcNode(PUBLIC_DATA_NODE, subnode), key, list);
	}

	//~--- methods --------------------------------------------------------------

	/**
	 * Method description
	 *
	 *
	 * @param name_param
	 *
	 * @return
	 *
	 * @throws NotAuthorizedException
	 * @throws TigaseDBException
	 */
	public Authorization unregister(String name_param)
			throws NotAuthorizedException, TigaseDBException {
		if ( !isAuthorized()) {
			return Authorization.FORBIDDEN;
		}

		// Some clients send plain user name and others send
		// jid as user name. Let's resolve this here.
		String user_name = BareJID.parseJID(name_param)[0];

		if ((user_name == null) || user_name.trim().isEmpty()) {
			user_name = name_param;
		}    // end of if (user_mame == null || user_name.equals(""))

		if (getUserName().equals(user_name)) {
			try {
				authRepo.removeUser(BareJID.toString(user_name, getDomain()));

				return Authorization.AUTHORIZED;
			} catch (UserNotFoundException e) {
				return Authorization.REGISTRATION_REQUIRED;
			} catch (TigaseDBException e) {
				log.log(Level.SEVERE, "Repository access exception.", e);

				return Authorization.INTERNAL_SERVER_ERROR;
			}    // end of catch
		} else {
			return Authorization.FORBIDDEN;
		}
	}

	private String calcNode(String base, String subnode) {
		if (subnode == null) {
			return base;
		}    // end of if (subnode == null)

		return base + "/" + subnode;
	}

	private Authorization changeRegistration(final String name_param, final String pass_param,
			final String email_param)
			throws NotAuthorizedException, TigaseDBException {
		if ((name_param == null) || name_param.equals("") || (pass_param == null)
				|| pass_param.equals("")) {
			return Authorization.BAD_REQUEST;
		}

		if (getUserName().equals(name_param)) {
			setRegistration(name_param, pass_param, email_param);

			return Authorization.AUTHORIZED;
		} else {
			return Authorization.NOT_AUTHORIZED;
		}
	}

	//~--- set methods ----------------------------------------------------------

	private void setRegistration(final String name_param, final String pass_param,
			final String email_param)
			throws TigaseDBException {
		try {
			authRepo.updatePassword(BareJID.toString(name_param, getDomain()), pass_param);

			if ((email_param != null) &&!email_param.equals("")) {
				repo.setData(BareJID.toString(name_param, getDomain()), "email", email_param);
			}
		} catch (UserNotFoundException e) {
			log.log(Level.WARNING, "Problem accessing reposiotry: ", e);

//    } catch (TigaseDBException e) {
//     log.log(Level.SEVERE, "Repository access exception.", e);
		}    // end of try-catch
	}
}    // RepositoryAccess


//~ Formatted in Sun Code Convention


//~ Formatted by Jindent --- http://www.jindent.com
