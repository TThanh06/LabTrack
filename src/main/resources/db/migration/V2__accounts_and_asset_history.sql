ALTER TABLE app_users ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE app_users ADD COLUMN auth_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE assets ADD COLUMN retired BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE loans ADD COLUMN asset_name VARCHAR(120);
ALTER TABLE loans ADD COLUMN asset_code VARCHAR(40);
UPDATE loans SET asset_name = (SELECT name FROM assets WHERE assets.id = loans.asset_id),
                 asset_code = (SELECT code FROM assets WHERE assets.id = loans.asset_id);
ALTER TABLE loans ALTER COLUMN asset_name SET NOT NULL;
ALTER TABLE loans ALTER COLUMN asset_code SET NOT NULL;
