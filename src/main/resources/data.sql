insert into audio_data (id, path, name, extension, content_hash) values
    (1,'uploads/songs/','RickAstley-NeverGonnaGiveYouUp','mp3','4bdb84873ccfb7f94190012dfbe2b62197aa8418fc492a0f0c1cd2f371fb646a'),
    (2,'uploads/songs/','【東方】Bad Apple!! ＰＶ【影絵】','mp3','db1e970f2cbf5acde9a700220f745c923c7f021a508217824b2f59904f3dbb4d'),
    (3,'uploads/songs/','Darude - Sandstorm','mp3','431110e14ecd02d2cdedd471a2615d94917b022de4866cb0ede3faa4bd844c68'),
    (4,'uploads/songs/','Crazy Frog - Axel F (Official Video)','mp3','551df6013cd03fe7382be86ff74a667e2acbf38556d6bdbfb47ae41dd8bf8cbe');

-- Explicit IDs do not advance PostgreSQL's identity sequence; sync it before auto-generated inserts.
SELECT setval(
    pg_get_serial_sequence('audio_data', 'id'),
    COALESCE((SELECT MAX(id) FROM audio_data), 1)
);