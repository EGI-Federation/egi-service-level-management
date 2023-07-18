package egi.eu;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

import java.util.Map;


/***
 * The configuration of the IMS
 */
@ConfigMapping(prefix = "egi.ims")
public interface IntegratedManagementSystemConfig {

    // Users must be members of this VO to use the IMS tools
    String vo();

    // Role names (in entitlements)
    // e.g. urn:mace:egi.eu:group:vo.tools.egi.eu:role=slm-manager#aai.egi.eu"
    Map<String, String> roles();
}
