package com.paladin.qos.service.data;


import com.paladin.framework.core.ServiceSupport;
import com.paladin.qos.mapper.data.DataProcessedDayMapper;
import com.paladin.qos.model.data.DataProcessedDay;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DataProcessedDayService extends ServiceSupport<DataProcessedDay> {

    @Autowired
    private DataProcessedDayMapper dataProcessedDayMapper;

    public boolean updateOrSave(DataProcessedDay model) {
        if (dataProcessedDayMapper.updateData(model) == 0) {
            if (dataProcessedDayMapper.insert(model) == 0) {
                return false;
            }
        }
        return true;
    }

}