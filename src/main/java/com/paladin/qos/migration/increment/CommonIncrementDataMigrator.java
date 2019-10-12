package com.paladin.qos.migration.increment;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.paladin.data.dynamic.SqlSessionContainer;
import com.paladin.framework.utils.time.DateFormatUtil;
import com.paladin.qos.dynamic.mapper.migration.DataMigrateMapper;
import com.paladin.qos.model.migration.DataMigration;
import com.paladin.qos.util.TimeUtil;

/**
 * 增量数据迁移实现类
 * 
 * @author TontoZhou
 * @since 2019年10月9日
 */
public class CommonIncrementDataMigrator implements IncrementDataMigrator {

	protected static final int MAX_SELECT_DATA_LIMIT = 10000;

	private static Logger logger = LoggerFactory.getLogger(CommonIncrementDataMigrator.class);

	protected SqlSessionContainer sqlSessionContainer;

	/**
	 * ID 唯一标识
	 */
	protected String id;

	/**
	 * 原始数据源
	 */
	protected String originDataSource;

	/**
	 * 原始数据库表名
	 */
	protected String originTableName;

	/**
	 * 原始数据源类型
	 */
	protected String originDataSourceType;

	/**
	 * 目标数据源
	 */
	protected String targetDataSource;

	/**
	 * 目标数据库表名
	 */
	protected String targetTableName;

	/**
	 * 更新时间字段
	 */
	protected String updateTimeField;

	/**
	 * 主键字段（可多个）
	 */
	protected Set<String> primaryKeyFields;

	/**
	 * 当前更新时间
	 */
	protected Date scheduleStartTime;

	/**
	 * 缺省开始时间
	 */
	protected Date defaultStartDate;

	/**
	 * 数据迁移描述
	 */
	protected DataMigration dataMigration;

	public CommonIncrementDataMigrator(DataMigration dataMigration, SqlSessionContainer sqlSessionContainer) {
		this.setDataMigration(dataMigration);
		this.sqlSessionContainer = sqlSessionContainer;
	}

	@Override
	public String getId() {
		return id;
	}

	public void setDataMigration(DataMigration dataMigration) {
		this.id = dataMigration.getId();
		this.originDataSource = dataMigration.getOriginDataSource();
		this.originDataSourceType = dataMigration.getOriginDataSourceType();
		this.originTableName = dataMigration.getOriginTableName();
		this.targetDataSource = dataMigration.getTargetDataSource();
		this.targetTableName = dataMigration.getTargetTableName();
		this.updateTimeField = dataMigration.getUpdateTimeField();
		this.defaultStartDate = dataMigration.getDefaultStartDate();

		String primaryKeyField = dataMigration.getPrimaryKeyField();
		String[] keyFields = primaryKeyField.split(",");

		this.primaryKeyFields = new HashSet<>();
		for (String keyField : keyFields) {
			primaryKeyFields.add(keyField);
		}

		this.dataMigration = dataMigration;
	}

	public DataMigration getDataMigration() {
		return dataMigration;
	}

	public MigrateResult migrateData(Date updateStartTime, Date updateEndTime, int migrateNum) {
		MigrateResult result = new MigrateResult(updateStartTime);

		updateStartTime = result.getMigrateEndTime();
		if (updateEndTime != null && updateStartTime.getTime() > updateEndTime.getTime()) {
			return result;
		}

		try {
			migrateData(updateStartTime, updateEndTime, result, migrateNum);
		} catch (Exception e) {
			logger.error("迁移数据失败！数据迁移ID：" + id + "，更新开始时间点：" + updateStartTime, e);
			result.setSuccess(false);
		}

		return result;
	}

	protected void migrateData(Date updateStartTime, Date updateEndTime, MigrateResult result, int selectDataLimit) {

		if (selectDataLimit > MAX_SELECT_DATA_LIMIT) {
			logger.error("超过最大查询限制数据！数据迁移ID：" + id + "，更新开始时间点：" + updateStartTime);
			result.setSuccess(false);
			return;
		}

		List<Map<String, Object>> datas = getData(updateStartTime, updateEndTime, selectDataLimit);

		if (datas != null) {
			int size = datas.size();
			if (size >= selectDataLimit) {
				Date start = getDataUpdateTime(datas.get(0));
				Date end = getDataUpdateTime(datas.get(size - 1));
				// 如果查出数据都是一个时间点，则需要扩大搜索范围以保证不进入死循环
				if (start.getTime() == end.getTime()) {
					migrateData(updateStartTime, updateEndTime, result, selectDataLimit * 2);
					return;
				}
			}

			for (Map<String, Object> data : datas) {
				Map<String, Object> needData = processData(data);
				boolean success = insertOrUpdateData(needData);
				if (success) {
					Date time = getDataUpdateTime(needData);
					result.setMigrateEndTime(time);
					result.setMigrateNum(result.getMigrateNum() + 1);
				} else {
					logger.error("更新或插入数据失败！数据迁移ID：" + id + "，更新开始时间点：" + updateStartTime);
					result.setSuccess(false);
					return;
				}
			}
		}
	}

	/**
	 * 获取原始数据
	 * 
	 * @param updateStartTime
	 * @param updateEndTime
	 * @param limit
	 * @return
	 */
	protected List<Map<String, Object>> getData(Date updateStartTime, Date updateEndTime, int limit) {
		sqlSessionContainer.setCurrentDataSource(originDataSource);
		DataMigrateMapper mapper = sqlSessionContainer.getSqlSessionTemplate().getMapper(DataMigrateMapper.class);
		SimpleDateFormat format = DateFormatUtil.getThreadSafeFormat("yyyy-MM-dd HH:ss:mm");

		String startTime = format.format(updateStartTime);
		String endTime = updateEndTime != null ? format.format(updateEndTime) : null;

		if (DataMigration.ORIGIN_DATA_SOURCE_TYPE_MYSQL.equals(originDataSourceType)) {
			return mapper.selectDataForMySql(originTableName, updateTimeField, startTime, endTime, limit);
		} else if (DataMigration.ORIGIN_DATA_SOURCE_TYPE_SQLSERVER.equals(originDataSourceType)) {
			return mapper.selectDataForSqlServer(originTableName, updateTimeField, startTime, endTime, limit);
		} else if (DataMigration.ORIGIN_DATA_SOURCE_TYPE_ORACLE.equals(originDataSourceType)) {
			return mapper.selectDataForOracle(originTableName, updateTimeField, startTime, endTime, limit);
		}

		throw new RuntimeException("不存在类型[" + originDataSourceType + "]数据库");
	}

	protected Date getDataUpdateTime(Map<String, Object> data) {
		return (Date) data.get(updateTimeField);
	}

	/**
	 * 重写该方法以达到处理数据，默认为不处理
	 * 
	 * @param data
	 * @return
	 */
	protected Map<String, Object> processData(Map<String, Object> data) {
		for (Entry<String, Object> entry : data.entrySet()) {
			Object value = entry.getValue();
			if (value instanceof Boolean) {
				data.put(entry.getKey(), ((Boolean) value) ? "1" : "0");
			}
		}
		return data;
	}

	/**
	 * 插入或更新数据到目标数据源
	 * 
	 * @param dataMap
	 * @return
	 */
	protected boolean insertOrUpdateData(Map<String, Object> dataMap) {
		sqlSessionContainer.setCurrentDataSource(targetDataSource);
		DataMigrateMapper sqlMapper = sqlSessionContainer.getSqlSessionTemplate().getMapper(DataMigrateMapper.class);

		Map<String, Object> primaryMap = new HashMap<>();

		for (String primaryKey : primaryKeyFields) {
			Object value = dataMap.get(primaryKey);
			if (value == null) {
				logger.error("主键不能为空！数据迁移ID：" + id);
				return false;
			}
			primaryMap.put(primaryKey, value);
			dataMap.remove(primaryKey);
		}

		if (sqlMapper.updateData(targetTableName, dataMap, primaryMap) > 0) {
			return true;
		} else {
			dataMap.putAll(primaryMap);
			return sqlMapper.insertData(targetTableName, dataMap) > 0;
		}
	}

	@Override
	public Date getScheduleStartTime() {
		if (scheduleStartTime == null) {
			sqlSessionContainer.setCurrentDataSource(targetDataSource);
			DataMigrateMapper sqlMapper = sqlSessionContainer.getSqlSessionTemplate().getMapper(DataMigrateMapper.class);
			List<Date> current = sqlMapper.getMaxUpdateTime(targetTableName, updateTimeField);
			scheduleStartTime = (current == null || current.size() == 0) ? defaultStartDate : current.get(0);
		}
		return scheduleStartTime;
	}

	@Override
	public void setScheduleStartTime(Date scheduleStartTime) {
		this.scheduleStartTime = scheduleStartTime;
	}

	@Override
	public Date getScheduleFilingDate() {
		int filingStrategy = dataMigration.getFilingStrategy();
		if (filingStrategy == DataMigration.FILING_STRATEGY_DEFAULT_NOW) {
			return null;
		} else if (filingStrategy == DataMigration.FILING_STRATEGY_DEFAULT_DAY) {
			return TimeUtil.getTodayBefore(dataMigration.getFilingStrategyParam1());
		} else if (filingStrategy == DataMigration.FILING_STRATEGY_DEFAULT_MONTH) {
			return TimeUtil.getTodayBeforeMonth(dataMigration.getFilingStrategyParam1());
		} else if (filingStrategy == DataMigration.FILING_STRATEGY_DEFAULT_YEAR) {
			return TimeUtil.getTodayBeforeYear(dataMigration.getFilingStrategyParam1());
		} else if (filingStrategy == DataMigration.FILING_STRATEGY_CUSTOM) {
			throw new RuntimeException("自定义归档策略需要开发者扩展重写代码");
		}

		throw new RuntimeException("不存在的归档策略代码：" + filingStrategy);
	}

	/**
	 * 每日调度任务时判断是否需要执行
	 * 
	 * @return
	 */
	public boolean needScheduleToday() {

		int scheduleStrategy = dataMigration.getScheduleStrategy();

		if (scheduleStrategy == DataMigration.SCHEDULE_STRATEGY_NO) {
			return false;
		} else if (scheduleStrategy == DataMigration.SCHEDULE_STRATEGY_EVERY_DAY) {
			return true;
		} else if (scheduleStrategy == DataMigration.SCHEDULE_STRATEGY_FIXED_DAY_OF_MONTH) {
			String dayStr = dataMigration.getScheduleStrategyParam2();
			String[] days = dayStr.split(",");
			Calendar c = Calendar.getInstance();
			String d = String.valueOf(c.get(Calendar.DAY_OF_MONTH));
			for (String day : days) {
				if (d.equals(day)) {
					return true;
				}
			}
			return false;
		} else if (scheduleStrategy == DataMigration.SCHEDULE_STRATEGY_FIXED_DAY_OF_YEAR) {
			String dayStr = dataMigration.getScheduleStrategyParam2();
			String[] days = dayStr.split(",");

			Calendar c = Calendar.getInstance();
			String d = String.valueOf(c.get(Calendar.MONTH) + 1) + "/" + String.valueOf(c.get(Calendar.DAY_OF_MONTH));

			for (String day : days) {
				if (d.equals(day)) {
					return true;
				}
			}
			return false;
		} else {
			throw new RuntimeException("还未实现策略：" + scheduleStrategy);
		}

	}

	private Boolean lock = false;

	@Override
	public boolean getLock() {
		synchronized (lock) {
			if (lock) {
				return false;
			} else {
				lock = true;
				return true;
			}
		}
	}

	@Override
	public void cancelLock() {
		synchronized (lock) {
			lock = false;
		}
	}

	private volatile long realTimeUpdateTime;

	@Override
	public long getRealTimeMigrateTime() {
		return realTimeUpdateTime;
	}

	@Override
	public void setRealTimeMigrateTime(long time) {
		realTimeUpdateTime = time;
	}
}
