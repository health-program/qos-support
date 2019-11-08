package com.paladin.qos.analysis.impl.gongwei.family;

import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;

import com.github.pagehelper.util.StringUtil;
import com.paladin.data.dynamic.SqlSessionContainer;
import com.paladin.qos.analysis.impl.gongwei.GongWeiDataProcessor;
import com.paladin.qos.dynamic.DSConstant;
import com.paladin.qos.dynamic.mapper.familydoctor.DataFamilyDoctorMapper;

/**
 * 严重肺结核患者签约率
 * 
 * @author MyKite
 * @version 2019年9月11日 下午4:31:31
 */
@Deprecated
public class FamilyTuberculosisSigningRate extends GongWeiDataProcessor {

	@Autowired
	private SqlSessionContainer sqlSessionContainer;

	public static final String EVENT_ID = "21027";

	@Override
	public String getEventId() {
		return EVENT_ID;
	}

	@Override
	public long getTotalNum(Date startTime, Date endTime, String unitId) {
		sqlSessionContainer.setCurrentDataSource(DSConstant.DS_GONGWEI);
		String unit = getMappingUnitId(unitId);
		if (StringUtil.isEmpty(unit)) {
			return 0;
		}
		return sqlSessionContainer.getSqlSessionTemplate().getMapper(DataFamilyDoctorMapper.class).singingServiceTotal(startTime, endTime, unit);
	}

	@Override
	public long getEventNum(Date startTime, Date endTime, String unitId) {
		sqlSessionContainer.setCurrentDataSource(DSConstant.DS_GONGWEI);
		String unit = getMappingUnitId(unitId);
		if (StringUtil.isEmpty(unit)) {
			return 0;
		}
		return sqlSessionContainer.getSqlSessionTemplate().getMapper(DataFamilyDoctorMapper.class).tuberculosisSigningRate(startTime, endTime, unit);
	}
}
