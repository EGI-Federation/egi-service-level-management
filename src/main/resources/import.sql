insert into slm.users (checkinuserid, fullname, email)
values ('e9c37aa0d1cf14c56e560f9f9915da6761f54383badb501a2867bc43581b835c@egi.eu', 'Levente Farkas', 'levente.farkas@egi.eu'),
       ('025166931789a0f57793a6092726c2ad89387a4cc167e7c63c5d85fc91021d18@egi.eu', 'Giuseppe La Rocca', 'giuseppe.larocca@egi.eu');

insert into slm.roles (role, name, version, status, changedon, changedescription, globalRoleId, tasks)
values ('process-staff', 'Process Staff', 1, 1, '2021-02-19T19:23:18', 'First version', 1, null),
       ('process-owner', 'Process Owner', 1, 1, '2021-02-19T19:23:18', 'First version', 2, null),
       ('process-manager', 'Process Manager', 1, 1, '2021-02-19T19:23:18', 'First version', 3, null),
       ('report-owner', 'Report Owner', 1, 1, '2021-02-19T19:23:18', 'First version', 4, null),

       ('process-developer', 'Process Developer', 1, 1, '2023-09-02T19:23:18', 'First version', null,
'- Make the necessary software changes to the SLM API so that requested changes to process, procedure, KPI, and role entities are implemented
- Improve the IMS front-end to allow exploiting all features of the SLM API'),

       ('catalog-owner', 'Service Catalog Owner', 1, 1, '2021-02-19T19:23:18', 'First version', null,
'- Maintain Service Catalogue
- Provide access to appropriate parts of the service catalogue(s) to its customers, users and other interested parties'),

       ('sla-owner', 'Service Level Agreement Owner', 1, 1, '2021-02-19T19:23:18', 'First version', null,
'- Maintain the SLA under his/her ownership and ensure it is specified and documented according to relevant specifications
- Evaluate the fulfillment of the SLA
- Ensure that violations of the targets defined in the SLA are identified and investigated to prevent future recurrence
- Perform regular reviews of the SLA
- Understand new or changed requirements of the SLA under his/her ownership, and initiate necessary updates or other follow-up actions'),

       ('ola-owner', 'Operational Level Agreement Owner', 1, 1, '2021-02-19T19:23:18', 'First version', null,
'- Maintain the OLA under his/her ownership and ensure it is specified and documented according to relevant specifications
- Evaluate the fulfillment of the OLA
- Ensure that violations of the targets defined in the OLA are identified and investigated to prevent future recurrence
- Perform regular reviews of the OLA
- Understand new or changed requirements of the OLA under his/her ownership, and initiate necessary updates or other follow-up actions'),

       ('ua-owner', 'Underpinning Agreement Owner', 1, 1, '2021-02-19T19:23:18', 'First version', null,
'- Maintain the UA under his/her ownership and ensure it is specified and documented according to relevant specifications
- Evaluate the fulfillment of the UA
- Ensure that violations of the targets defined in the UA are identified and investigated to prevent future recurrence
- Perform regular reviews of the UA
- Understand new or changed requirements of the UA under his/her ownership, and initiate necessary updates or other follow-up actions');

insert into slm.role_editor_map (role_id, user_id)
values (1, 2),
       (2, 2),
       (3, 2),
       (4, 1),
       (5, 2),
       (6, 2),
       (7, 2),
       (8, 2),
       (9, 2);

insert into slm.process (goals, scope, status, reviewfrequency, frequencyunit, nextreview, changedon, changedescription, contact)
VALUES ('The primary purpose of this process is...',
        'The scope of this process is...',
        0, 1, 'year', '2021-05-14', '2021-02-19T19:23:18', 'First draft', null),

       ('The primary purpose of this process is to
- Define, agree (with customers), and monitor service level agreements (SLAs)
- Define, agree (with federation members and suppliers), and monitor operation level agreements (OLAs)
- Define, agree (with suppliers), and monitor underpinning agreements (UA).',
        '- **Internal catalogue** - services are covered by Corporate SLA, no custom SLAs are foreseen.
- **External catalogue**:
    - **Category**:  dedicated custom SLA (VO SLA) upon customer request. If no VO SLA in place, Corporate SLA is applicable.
        - Online Storage
        - Check-in
        - EGI Notebooks
        - Cloud Compute
        - Cloud Container Compute
        - High-Throughput Compute
    - **Training**: Corporate SLA together with contract signed for training delivery
        - FitSM training
        - ISO27k training
    - **Other**: Corporate SLA, no custom SLAs are foreseen.',
        2, 8, 'day', '2023-11-14', '2021-05-14T22:03:18', 'Updated version', 'contact@egi.eu');

insert into slm.process_editor_map (process_id, user_id)
values (1, 2),
       (2, 2);

insert into slm.process_requirements (code, source, requirement)
values ('PR2.2', 'FitSM', 'For all _services_ delivered to customers, **SLAs** shall be in place.'),
       ('PR2.3', 'FitSM', '**SLAs** shall be reviewed at planned intervals.'),
       (null, 'ISO20K:2018', 'The catalogue of services shall include:
- the dependencies between services, and
- service components.');

insert into slm.process_requirements_map(process_id, requirement_id)
values (1, 1),
       (2, 1),
       (2, 2),
       (2, 3);

insert into slm.process_requirement_responsibles_map(requirement_id, user_id)
values (1, 1),
       (2, 1),
       (2, 2),
       (3, 2);

insert into slm.process_interfaces(direction, interfaceswith, description, relevantmaterial)
values ('In', 'CAPM', 'Reflecting demands, planned upgrades, downgrades and re-allocations of resources.', 'Capacity Plan Database'),
       ('Out', 'Internal', 'Data is gathered under http://argo.egi.eu/', 'Records of monitoring the performance of services and internal groups providing service components

EGI OLA Services reporting data'),
       ('Out', 'ISRM, SRM, CRM, SACM, CAPM, IS', 'Agreed Service Level Agreement, Operational Level Agreement, Underpinning Agreements', '**SLA/OLA/UA** database

- VO SLA OLAs
- OLA and UA
- [Agreements](https://egi.eu)');

insert into slm.process_interfaces_map(process_id, interface_id)
values (1, 2),
       (2, 1),
       (2, 2),
       (2, 3);
