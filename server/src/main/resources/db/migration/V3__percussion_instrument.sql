ALTER TABLE songs DROP CONSTRAINT songs_instrument_check;
ALTER TABLE songs ADD CONSTRAINT songs_instrument_check CHECK (instrument BETWEEN 0 AND 128);
ALTER TABLE bar_records DROP CONSTRAINT bar_records_instrument_check;
ALTER TABLE bar_records ADD CONSTRAINT bar_records_instrument_check CHECK (instrument BETWEEN 0 AND 128);
ALTER TABLE user_settings DROP CONSTRAINT user_settings_instrument_check;
ALTER TABLE user_settings ADD CONSTRAINT user_settings_instrument_check CHECK (instrument BETWEEN 0 AND 128);
