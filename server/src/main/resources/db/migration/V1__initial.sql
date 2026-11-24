CREATE TABLE app_users (
  login_id varchar(24) PRIMARY KEY CHECK (login_id ~ '^[A-Za-z0-9_]{3,24}$'),
  nickname varchar(20) NOT NULL CHECK (length(trim(nickname))>0),
  password_salt text NOT NULL, password_hash text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE songs (
  id uuid PRIMARY KEY, owner_id varchar(24) NOT NULL REFERENCES app_users(login_id),
  title varchar(40) NOT NULL CHECK(length(trim(title))>0),
  type varchar(8) NOT NULL CHECK(type IN ('PERSONAL','SHARED')),
  bpm integer NOT NULL CHECK(bpm BETWEEN 40 AND 200),
  instrument integer NOT NULL CHECK(instrument BETWEEN 0 AND 127),
  version integer NOT NULL CHECK(version>=1), created_at timestamptz NOT NULL
);
CREATE TABLE memberships (
  song_id uuid NOT NULL REFERENCES songs(id), user_id varchar(24) NOT NULL REFERENCES app_users(login_id),
  role varchar(6) NOT NULL CHECK(role IN ('OWNER','MEMBER')), active boolean NOT NULL DEFAULT true,
  joined_at timestamptz NOT NULL, PRIMARY KEY(song_id,user_id)
);
CREATE UNIQUE INDEX one_active_owner ON memberships(song_id) WHERE role='OWNER' AND active;
CREATE TABLE bar_records (
  id uuid PRIMARY KEY, song_id uuid NOT NULL REFERENCES songs(id),
  author_id varchar(24) NOT NULL REFERENCES app_users(login_id), record_date date NOT NULL,
  memo varchar(20) NOT NULL CHECK(position(chr(10) in memo)=0 AND position(chr(13) in memo)=0),
  bpm integer NOT NULL CHECK(bpm BETWEEN 40 AND 200), instrument integer NOT NULL CHECK(instrument BETWEEN 0 AND 127),
  version integer NOT NULL CHECK(version>=1), schema_version integer NOT NULL CHECK(schema_version=2),
  UNIQUE(song_id,author_id,record_date)
);
CREATE TABLE notes (
  record_id uuid NOT NULL REFERENCES bar_records(id) ON DELETE CASCADE,
  order_no integer NOT NULL CHECK(order_no BETWEEN 0 AND 127),
  start_tick integer NOT NULL CHECK(start_tick>=0 AND start_tick<1920 AND start_tick%120=0),
  duration_tick integer NOT NULL CHECK(duration_tick>=120 AND duration_tick%120=0 AND duration_tick<=1920),
  pitch integer NOT NULL CHECK(pitch BETWEEN 48 AND 84), velocity integer NOT NULL CHECK(velocity BETWEEN 1 AND 127),
  articulation varchar(8) NOT NULL CHECK(articulation IN ('NORMAL','STACCATO')),
  PRIMARY KEY(record_id,order_no), CHECK(start_tick+duration_tick<=1920)
);
CREATE TABLE rests (
  record_id uuid NOT NULL REFERENCES bar_records(id) ON DELETE CASCADE,
  order_no integer NOT NULL CHECK(order_no BETWEEN 0 AND 15),
  start_tick integer NOT NULL CHECK(start_tick>=0 AND start_tick<1920 AND start_tick%120=0),
  duration_tick integer NOT NULL CHECK(duration_tick>=120 AND duration_tick%120=0 AND duration_tick<=1920),
  PRIMARY KEY(record_id,order_no), CHECK(start_tick+duration_tick<=1920)
);
CREATE TABLE expression_ranges (
  record_id uuid NOT NULL REFERENCES bar_records(id) ON DELETE CASCADE,
  order_no integer NOT NULL CHECK(order_no BETWEEN 0 AND 15),
  start_tick integer NOT NULL CHECK(start_tick>=0 AND start_tick%120=0),
  end_tick integer NOT NULL CHECK(end_tick<=1920 AND end_tick%120=0 AND start_tick<end_tick),
  kind varchar(10) NOT NULL CHECK(kind='CRESCENDO'),
  start_velocity integer NOT NULL CHECK(start_velocity>=1),
  end_velocity integer NOT NULL CHECK(end_velocity<=127 AND end_velocity>start_velocity),
  PRIMARY KEY(record_id,order_no)
);
CREATE TABLE invite_codes (
  code_hash char(64) PRIMARY KEY, song_id uuid NOT NULL REFERENCES songs(id),
  expires_at timestamptz NOT NULL, active boolean NOT NULL DEFAULT true
);
CREATE TABLE user_settings (
  user_id varchar(24) PRIMARY KEY REFERENCES app_users(login_id),
  volume integer NOT NULL CHECK(volume BETWEEN 0 AND 100), instrument integer NOT NULL CHECK(instrument BETWEEN 0 AND 127),
  count_in boolean NOT NULL, metronome boolean NOT NULL, count_in_beats integer NOT NULL CHECK(count_in_beats BETWEEN 1 AND 8)
);
CREATE TABLE auth_sessions (
  token_hash char(64) PRIMARY KEY, user_id varchar(24) NOT NULL REFERENCES app_users(login_id),
  expires_at timestamptz NOT NULL, revoked_at timestamptz
);
CREATE TABLE save_requests (
  user_id varchar(24) NOT NULL REFERENCES app_users(login_id), request_id uuid NOT NULL,
  content_hash char(64) NOT NULL, record_id uuid NOT NULL REFERENCES bar_records(id),
  result_version integer NOT NULL CHECK(result_version>=1), response_json jsonb NOT NULL,
  PRIMARY KEY(user_id,request_id)
);
CREATE INDEX records_by_song_date ON bar_records(song_id,record_date);
CREATE INDEX sessions_by_user ON auth_sessions(user_id);
