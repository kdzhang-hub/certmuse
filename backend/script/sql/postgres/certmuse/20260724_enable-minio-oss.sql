-- CertMuse local MinIO baseline. Credentials are injected from /etc/certmuse/runtime.env,
-- never stored by this migration or written back to sys_oss_config.
UPDATE sys_oss_config
SET bucket_name = 'certmuse',
    endpoint = 'host.docker.internal:9000',
    is_https = 'N',
    access_policy = '0',
    status = 'Y'
WHERE config_key = 'minio';

UPDATE sys_menu
SET visible = '0'
WHERE menu_id = 1761400000000000118
  AND visible <> '0';
