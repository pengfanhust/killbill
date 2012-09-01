DROP TABLE IF EXISTS tenants;
CREATE TABLE tenants (
    record_id int(11) unsigned NOT NULL AUTO_INCREMENT,
    id char(36) NOT NULL,
    external_key varchar(128) NULL,
    api_key varchar(128) NULL,
    created_date datetime NOT NULL,
    created_by varchar(50) NOT NULL,
    updated_date datetime DEFAULT NULL,
    updated_by varchar(50) DEFAULT NULL,
    PRIMARY KEY(record_id)
) ENGINE=innodb;
CREATE UNIQUE INDEX tenants_id ON tenants(id);
CREATE UNIQUE INDEX tenants_api_key ON tenants(api_key);
