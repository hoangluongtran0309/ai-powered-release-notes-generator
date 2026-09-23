-- The UI language one person chose (ADR-0027). NULL means the browser decides, which is
-- also what every account created before this migration keeps. The value is a canonical
-- IETF language tag narrowed to a primary subtag, so only the deployment's configured UI
-- languages ever reach this column.
ALTER TABLE app_users ADD COLUMN ui_locale VARCHAR(16);

ALTER TABLE app_users ADD CONSTRAINT app_users_ui_locale_canonical
    CHECK (ui_locale IS NULL OR (ui_locale = BTRIM(ui_locale) AND ui_locale <> ''));
