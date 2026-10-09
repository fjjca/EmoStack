package org.example.aispingboot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.aispingboot.entity.EmotionLog;

@Mapper
public interface EmotionLogMapper extends BaseMapper<EmotionLog> {
}