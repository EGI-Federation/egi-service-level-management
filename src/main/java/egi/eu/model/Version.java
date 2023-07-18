package egi.eu.model;

import java.util.Date;


/**
 * Details of a version
 */
public abstract class Version {

    // Change tracking
    int version;
    Date changeAt;
    String changeBy;
    String changeDescription;
}
