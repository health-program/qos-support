package com.paladin.qos.analysis.impl.yiyuan.performance;

import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.paladin.data.dynamic.SqlSessionContainer;
import com.paladin.qos.analysis.impl.yiyuan.YiyuanDataProcessor;
import com.paladin.qos.dynamic.mapper.yiyuan.performance.PerformanceMapper;

/**医疗服务收入
 * @author MyKite
 * @version 2019年9月27日 上午9:43:09 
 */
@Component
public class MedicalServiceFee extends YiyuanDataProcessor{

	@Autowired
	private SqlSessionContainer sqlSessionContainer;

	public static final String EVENT_ID = "60006";

	@Override
	public String getEventId() {
		return EVENT_ID;
	}

	@Override
	public long getTotalNum(Date startTime, Date endTime, String unitId) {
		sqlSessionContainer.setCurrentDataSource(getDataSourceByUnit(unitId));
		return (long)(sqlSessionContainer.getSqlSessionTemplate().getMapper(PerformanceMapper.class).getMedicalServiceFee(startTime, endTime)*100);
	}

	@Override
	public long getEventNum(Date startTime, Date endTime, String unitId) {
		return 0;
	}
}
