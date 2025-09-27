-- 1️⃣ Create database
CREATE DATABASE IF NOT EXISTS wordgame;
USE wordgame;

-- 2️⃣ Users table
CREATE TABLE IF NOT EXISTS users (
    user_id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(50) NOT NULL
);

-- 3️⃣ Words table (20 five-letter words)
CREATE TABLE IF NOT EXISTS words (
    id INT AUTO_INCREMENT PRIMARY KEY,
    word_text VARCHAR(5) NOT NULL
);

INSERT IGNORE INTO words (word_text) VALUES
('APPLE'),('BRAIN'),('CHAIR'),('DREAM'),('EAGLE'),
('FRUIT'),('GRAPE'),('HOUSE'),('INDEX'),('JUMPY'),
('KNIFE'),('LEMON'),('MONEY'),('NORTH'),('OCEAN'),
('PEARL'),('QUEEN'),('RIVER'),('SUGAR'),('TIGER');

-- 4️⃣ Games table
CREATE TABLE IF NOT EXISTS games (
    game_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT,
    word_id INT,
    date_played DATETIME,
    attempts_used INT DEFAULT 0,
    correct BOOLEAN DEFAULT FALSE,
    status VARCHAR(20) DEFAULT 'ONGOING',
    FOREIGN KEY (user_id) REFERENCES users(user_id),
    FOREIGN KEY (word_id) REFERENCES words(id)
);

-- 5️⃣ Guesses table
CREATE TABLE IF NOT EXISTS guesses (
    guess_id INT AUTO_INCREMENT PRIMARY KEY,
    game_id INT,
    guess_text VARCHAR(5),
    attempt_no INT,
    FOREIGN KEY (game_id) REFERENCES games(game_id)
);

-- 6️⃣ Admin daily report example query
SELECT COUNT(DISTINCT u.username) AS users_played,
    SUM(CASE WHEN g.correct THEN 1 ELSE 0 END) AS correct_guesses
FROM games g
JOIN users u ON g.user_id = u.user_id
WHERE DATE(g.date_played) = CURDATE();

-- 7️⃣ Admin per-user report example query
SELECT u.username, g.status, w.word_text AS given_word, gu.guess_text, gu.attempt_no, g.date_played, g.correct
FROM games g
JOIN users u ON g.user_id = u.user_id
JOIN words w ON g.word_id = w.id
LEFT JOIN guesses gu ON g.game_id = gu.game_id
ORDER BY u.username, g.date_played, g.game_id, gu.attempt_no;
