-- Poster photo for each movie (a public uploaded file); without one the app draws a poster from the two colours.
ALTER TABLE movies
    ADD COLUMN poster_file_id BIGINT NULL,
    ADD CONSTRAINT fk_movies_poster FOREIGN KEY (poster_file_id) REFERENCES uploaded_files (id) ON DELETE SET NULL;
