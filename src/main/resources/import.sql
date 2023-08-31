insert into slm.users (checkinuserid, fullname, email)
values (6166, 'Levente Farkas', 'levente@egi.eu'),
       (171, 'Giuseppe La Rocca', 'giuseppe@egi.eu');

insert into slm.processes (goals, scope, status, reviewfrequency, frequencyunit, nextreview, approvedon, changedon, changedescription, contact)
VALUES ('The primary purpose of this process is to
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
        0, 1, 'year', '2021-05-14', null, '2021-02-19T19:23:18', 'first version', 'aaa@bbb.com'),

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
        2, 8, 'day', '2023-11-14', '2021-05-14', '2021-05-14T22:03:18', 'next version', 'contact@egi.eu');

insert into slm.process_editor (process_id, user_id)
values (1, 1),
       (2, 2);

insert into slm.process_approver (process_id, user_id)
values (2, 2);

insert into slm.requirements (code, source, requirement)
values ('PR2.2', 'FitSM', 'For all _services_ delivered to customers, **SLAs** shall be in place.'),
       ('PR2.3', 'FitSM', '**SLAs** shall be reviewed at planned intervals.'),
       (null, 'ISO20K:2018', 'The catalogue of services shall include:
- the dependencies between services, and
- service components.');

insert into slm.process_requirements(process_id, requirement_id)
values (1, 1),
       (2, 1),
       (2, 2),
       (2, 3);

insert into slm.requirement_responsibles(requirement_id, user_id)
values (1, 1),
       (2, 1),
       (2, 2),
       (3, 2);

insert into slm.interfaces(direction, interfaceswith, description, relevantmaterial)
values ('In', 'CAPM', 'Reflecting demands, planned upgrades, downgrades and re-allocations of resources.', 'Capacity Plan Database'),
       ('Out', 'Internal', 'Data is gathered under http://argo.egi.eu/', 'Records of monitoring the performance of services and internal groups providing service components

EGI OLA Services reporting data'),
       ('Out', 'ISRM, SRM, CRM, SACM, CAPM, IS', 'Agreed Service Level Agreement, Operational Level Agreement, Underpinning Agreements', '**SLA/OLA/UA** database

- VO SLA OLAs
- OLA and UA
- [Agreements](https://egi.eu)');

insert into slm.process_interfaces(process_id, interface_id)
values (1, 2),
       (2, 1),
       (2, 2),
       (2, 3);


