package com.paladin.qos.analysis;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.paladin.qos.core.DataTask;
import com.paladin.qos.model.data.DataEvent;
import com.paladin.qos.model.data.DataUnit;
import com.paladin.qos.service.analysis.AnalysisService;
import com.paladin.qos.util.TimeUtil;

public class DataProcessTask extends DataTask {

	private static Logger logger = LoggerFactory.getLogger(DataProcessTask.class);

	private DataProcessor processor;
	private AnalysisService analysisService;

	private Map<String, Long> lastProcessedDayMap;

	public DataProcessTask(DataProcessor processor, AnalysisService analysisService) {
		super(processor.getEventId());
		this.setConfiguration(processor.getDataEvent());
		this.processor = processor;
		this.analysisService = analysisService;
	}

	@Override
	public void doTask() {

		if (lastProcessedDayMap == null) {
			lastProcessedDayMap = getLastProcessedDay();
		}

		// 归档日期，该日期之后的数据都是很可能会变的，所以标识未未确认
		Date filingDate = getScheduleFilingDate();
		long filingTime = filingDate == null ? 0 : filingDate.getTime();

		DataEvent event = processor.getDataEvent();
		int maximumProcess = event.getMaximumProcess();

		// 截止日期不能超过今天
		long endTime = TimeUtil.toDayTime(new Date()).getTime();
		int eventCount = 0;

		try {
			for (DataUnit unit : processor.getTargetUnits()) {
				String unitId = unit.getId();

				long startTime = lastProcessedDayMap.get(unitId);

				if (filingTime > 0) {
					if (startTime > filingTime) {
						startTime = filingTime;
					}
				}

				startTime += TimeUtil.MILLIS_IN_DAY;
				int count = 0;

				while (startTime <= endTime) {
					Date start = new Date(startTime);
					startTime += TimeUtil.MILLIS_IN_DAY;
					Date end = new Date(startTime);
					boolean confirmed = start.getTime() <= filingTime;
					boolean success = analysisService.processDataForOneDay(start, end, unitId, processor, confirmed);

					if (!success) {
						break;
					}

					count++;
					// 更新处理事件
					lastProcessedDayMap.put(unitId, start.getTime());

					if (maximumProcess > 0 && count >= maximumProcess) {
						break;
					}

					if (!isRealTime() && isThreadFinished()) {
						break;
					}
				}
				eventCount += count;
			}
			logger.info("数据预处理任务[" + getId() + "]执行完毕，共处理数据：" + eventCount + "条");
		} catch (Exception e) {
			logger.error("数据预处理异常[ID:" + getId() + "]", e);
		}
	}

	/**
	 * 读取最近一次处理日
	 * 
	 * @return
	 */
	private Map<String, Long> getLastProcessedDay() {
		SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd");
		String eventId = getId();
		Map<String, Long> lastProcessedDayMap = new HashMap<>();
		for (DataUnit unit : processor.getTargetUnits()) {
			String unitId = unit.getId();
			Integer dayNum = analysisService.getCurrentDayOfEventAndUnit(eventId, unitId);
			if (dayNum != null) {
				Date date;
				try {
					date = format.parse(String.valueOf(dayNum));
					lastProcessedDayMap.put(unitId, date.getTime());
				} catch (ParseException e) {
					e.printStackTrace();
				}
			} else {
				// 如果一个数据都没，则从默认时间开始
				lastProcessedDayMap.put(unitId, processor.getDataEvent().getProcessStartDate().getTime() - TimeUtil.MILLIS_IN_DAY);
			}
		}
		return lastProcessedDayMap;
	}

}
