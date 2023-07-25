package egi.checkin;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;
import org.eclipse.microprofile.openapi.annotations.media.Schema;


/***
 * The EGI Check-in configuration
 */
@Schema(hidden=true)
@ConfigMapping(prefix = "egi.checkin")
public interface CheckinConfig {

    // Credentials to call the Check-in COManage API
    String username();
    String password();

    @WithName("co-id")
    String coId();

    @WithName("cache-vo-members")
    int cacheMembers();
}
