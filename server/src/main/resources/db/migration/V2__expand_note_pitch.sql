ALTER TABLE notes DROP CONSTRAINT notes_pitch_check;
ALTER TABLE notes ADD CONSTRAINT notes_pitch_check CHECK (pitch BETWEEN 0 AND 127);
