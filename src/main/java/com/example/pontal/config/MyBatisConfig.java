package com.example.pontal.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

//MyBatisのMapperインターフェースを登録する設定
//メインクラスに置くと@WebMvcTestでも読み込まれてしまうため、別クラスに分けている
@Configuration
@MapperScan("com.example.pontal.mapper")
public class MyBatisConfig {
}
