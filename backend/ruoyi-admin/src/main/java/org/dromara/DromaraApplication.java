package org.dromara;

import org.dromara.common.json.config.JacksonConfig;
import org.dromara.common.json.config.JsonEnhancementConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;
import org.springframework.context.annotation.Import;

/**
 * 启动程序
 *
 * @author Lion Li
 */

@SpringBootApplication
@Import({JacksonConfig.class, JsonEnhancementConfig.class})
public class DromaraApplication {

    /**
     * 应用启动入口。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(DromaraApplication.class);
        application.setApplicationStartup(new BufferingApplicationStartup(2048));
        application.run(args);
        System.out.println("CertMuse started successfully.");
    }

}
