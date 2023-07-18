package egi.checkin;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;
import org.eclipse.microprofile.openapi.annotations.media.Schema;


/***
 * The OIDC configuration of EGI Check-in
 */
@Schema(hidden=true)
@ConfigMapping(prefix = "quarkus.oidc")
public interface CheckinConfig {

    @WithName("auth-server-url")
    String instance();
}
