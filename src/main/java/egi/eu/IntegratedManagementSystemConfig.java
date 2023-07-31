package egi.eu;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;


/***
 * The configuration of the IMS
 */
@ConfigMapping(prefix = "egi.ims")
@ApplicationScoped
public interface IntegratedManagementSystemConfig {

    // Users must be members of this VO to use the IMS tools
    String vo();

    // Users must be members of this group to participate in SLM
    String group();

    // Role names (in entitlements)
    // e.g. urn:mace:egi.eu:group:vo.tools.egi.eu:slm:role=process-manager#aai.egi.eu"
    Map<String, String> roles();
}
