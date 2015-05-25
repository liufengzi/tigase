package tigase.monitor.tasks;

import java.util.Date;
import java.util.HashSet;

import tigase.disteventbus.EventBus;
import tigase.form.Field;
import tigase.form.Form;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Inject;
import tigase.monitor.InfoTask;
import tigase.monitor.MonitorComponent;
import tigase.server.monitor.MonitorRuntime;
import tigase.util.DateTimeFormatter;
import tigase.xml.Element;

@Bean(name = "load-checker-task")
public class LoadCheckerTask extends AbstractConfigurableTimerTask implements InfoTask {

	private final static DateTimeFormatter dtf = new DateTimeFormatter();

	public static final String MONITOR_EVENT_NAME = "LoadAverageMonitorEvent";

	@Inject
	private MonitorComponent component;

	@Inject
	private EventBus eventBus;

	private long maxAverageLoad = 10;

	@Inject
	private MonitorRuntime runtime;

	private final HashSet<String> triggeredEvents = new HashSet<String>();

	@Override
	public Form getCurrentConfiguration() {
		Form form = super.getCurrentConfiguration();

		form.addField(Field.fieldTextSingle("maxAverageLoad", String.valueOf(maxAverageLoad),
				"Alarm when AverageLoad is bigger than"));

		return form;
	}

	@Override
	public Form getTaskInfo() {
		Form result = new Form("", "Load Information", "");
		result.addField(Field.fieldTextSingle("averageLoad", Double.toString(runtime.getLoadAverage()), "Load Average"));

		return result;
	}

	@Override
	protected void run() {
		double curAverageLoad = runtime.getLoadAverage();
		if (curAverageLoad >= maxAverageLoad) {
			Element event = new Element(MONITOR_EVENT_NAME, new String[] { "xmlns" },
					new String[] { MonitorComponent.EVENTS_XMLNS });
			event.addChild(new Element("timestamp", "" + dtf.formatDateTime(new Date())));
			event.addChild(new Element("hostname", component.getDefHostName().toString()));
			event.addChild(new Element("averageLoad", Double.toString(curAverageLoad)));
			event.addChild(new Element("message", "Average Load is higher than " + maxAverageLoad + " and it is equals "
					+ Double.toString(curAverageLoad)));

			if (!triggeredEvents.contains(event.getName())) {
				eventBus.fire(event);
				triggeredEvents.add(event.getName());
			}
		} else {
			triggeredEvents.remove(MONITOR_EVENT_NAME);
		}
	}

	@Override
	public void setNewConfiguration(Form form) {
		Field tmp = form.get("maxAverageLoad");
		if (tmp != null) {
			this.maxAverageLoad = Long.parseLong(tmp.getValue());
		}

		super.setNewConfiguration(form);
	}

}
