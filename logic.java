import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;

public class logic {
    private static final String DB_URL = "jdbc:sqlite:wordgame.db";
    private static final Scanner sc = new Scanner(System.in);

    // ANSI colors
    private static final String RESET = "\u001B[0m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String GREY = "\u001B[90m";

    public static void main(String[] args) {
        try {
            Class.forName("org.sqlite.JDBC");
            Connection conn = DriverManager.getConnection(DB_URL);
            setupDatabase(conn);

            System.out.println("Welcome to Word Guess Game!");
            System.out.print("Do you want to Login or Register? (l/r): ");
            String choice = sc.nextLine().trim().toLowerCase();

            String username;
            if (choice.equals("r")) {
                username = register(conn);
            } else {
                username = login(conn);
            }

            System.out.println("? Login successful! (Password: *****)");
            System.out.print("Are you Admin or Player? (admin/player): ");
            String role = sc.nextLine().trim().toLowerCase();

            if (role.equals("admin")) {
                adminMenu(conn);
            } else {
                playerMenu(conn, username);
            }

            conn.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ---------- Setup DB ----------
    private static void setupDatabase(Connection conn) throws SQLException {
        Statement stmt = conn.createStatement();
        stmt.execute("CREATE TABLE IF NOT EXISTS users (user_id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE, password TEXT)");
        stmt.execute("CREATE TABLE IF NOT EXISTS words (id INTEGER PRIMARY KEY AUTOINCREMENT, word_text TEXT)");
        stmt.execute("CREATE TABLE IF NOT EXISTS games (game_id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER, word_id INTEGER, date_played TEXT, attempts_used INTEGER DEFAULT 0, correct INTEGER DEFAULT 0, status TEXT DEFAULT 'ONGOING', FOREIGN KEY(user_id) REFERENCES users(user_id), FOREIGN KEY(word_id) REFERENCES words(id))");
        stmt.execute("CREATE TABLE IF NOT EXISTS guesses (guess_id INTEGER PRIMARY KEY AUTOINCREMENT, game_id INTEGER, guess_text TEXT, attempt_no INTEGER, FOREIGN KEY(game_id) REFERENCES games(game_id))");

        // Insert words only once
        ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM words");
        if (rs.next() && rs.getInt(1) == 0) {
            String[] words = {"APPLE","BRAIN","CHAIR","DREAM","EAGLE","FRUIT","GRAPE","HOUSE","INDEX","JUMPY","KNIFE","LEMON","MONEY","NORTH","OCEAN","PEARL","QUEEN","RIVER","SUGAR","TIGER"};
            PreparedStatement ps = conn.prepareStatement("INSERT INTO words(word_text) VALUES(?)");
            for (String w : words) {
                ps.setString(1, w);
                ps.executeUpdate();
            }
        }
    }

    // ---------- User Login/Register ----------
    private static String register(Connection conn) throws SQLException {
        String uname = "";
        while (true) {
            System.out.print("Enter username (min 5 letters): ");
            uname = sc.nextLine().trim();
            if (!Pattern.matches("[a-zA-Z]{5,}", uname)) {
                System.out.println("❌ Username must be at least 5 letters.");
                continue;
            }
            break;
        }

        String pass = "";
        while (true) {
            System.out.print("Enter password (min 5 chars, letter+number+@$%*): ");
            pass = sc.nextLine();
            if (!Pattern.matches("(?=.*[a-zA-Z])(?=.*\\d)(?=.*[@$%*]).{5,}", pass)) {
                System.out.println("❌ Password specifications not followed.");
                continue;
            }
            break;
        }

        try {
            PreparedStatement ps = conn.prepareStatement("INSERT INTO users(username,password) VALUES(?,?)");
            ps.setString(1, uname);
            ps.setString(2, pass);
            ps.executeUpdate();
            System.out.println("? Registered successfully!");
        } catch (SQLException e) {
            System.out.println("❌ Username already exists.");
        }

        return login(conn);
    }

    private static String login(Connection conn) throws SQLException {
        System.out.print("Enter username: ");
        String uname = sc.nextLine();
        System.out.print("Enter password: ");
        String pass = sc.nextLine();

        PreparedStatement ps = conn.prepareStatement("SELECT user_id FROM users WHERE username=? AND password=?");
        ps.setString(1, uname);
        ps.setString(2, pass);
        ResultSet rs = ps.executeQuery();

        if (rs.next()) return uname;
        else {
            System.out.println("❌ Invalid credentials.");
            return login(conn);
        }
    }

    // ---------- Player Menu ----------
    private static void playerMenu(Connection conn, String username) throws SQLException {
        int userId = getUserId(conn, username);

        // Check for ongoing paused game
        PreparedStatement psPaused = conn.prepareStatement("SELECT * FROM games WHERE user_id=? AND status='PAUSED' ORDER BY game_id LIMIT 1");
        psPaused.setInt(1, userId);
        ResultSet rsPaused = psPaused.executeQuery();

        if (rsPaused.next()) {
            System.out.println("Resuming your paused game!");
            int gameId = rsPaused.getInt("game_id");
            int wordId = rsPaused.getInt("word_id");
            String targetWord = getWordById(conn, wordId);
            playWord(conn, userId, gameId, targetWord, true);
        }

        // Check how many words already played today (status COMPLETED or FAILED)
        PreparedStatement psCount = conn.prepareStatement("SELECT COUNT(*) FROM games WHERE user_id=? AND DATE(date_played)=DATE('now') AND status<>'PAUSED'");
        psCount.setInt(1, userId);
        ResultSet rsCount = psCount.executeQuery();
        int wordsPlayed = 0;
        if (rsCount.next()) wordsPlayed = rsCount.getInt(1);

        int wordsToPlay = 3 - wordsPlayed;
        if (wordsToPlay <= 0) {
            System.out.println("You have already played 3 words today. Come back tomorrow!");
            return;
        }

        // Fetch all words
        List<Integer> wordIds = new ArrayList<>();
        List<String> wordTexts = new ArrayList<>();
        Statement stmt = conn.createStatement();
        ResultSet rsWords = stmt.executeQuery("SELECT id, word_text FROM words");
        while (rsWords.next()) {
            wordIds.add(rsWords.getInt("id"));
            wordTexts.add(rsWords.getString("word_text"));
        }

        Random rand = new Random();

        for (int i = 0; i < wordsToPlay; i++) {
            int randomIndex = rand.nextInt(wordIds.size());
            int wordId = wordIds.get(randomIndex);
            String targetWord = wordTexts.get(randomIndex);

            // Insert new game
            PreparedStatement psGame = conn.prepareStatement("INSERT INTO games(user_id,word_id,date_played,status) VALUES(?,?,datetime('now'),'ONGOING')", Statement.RETURN_GENERATED_KEYS);
            psGame.setInt(1, userId);
            psGame.setInt(2, wordId);
            psGame.executeUpdate();
            ResultSet keys = psGame.getGeneratedKeys();
            keys.next();
            int gameId = keys.getInt(1);

            boolean stopped = playWord(conn, userId, gameId, targetWord, false);
            if (stopped) {
                System.out.println("Game paused. You can resume within 24 hrs.");
                return;
            }
        }

        System.out.println("Game session over for today!");
    }

    private static boolean playWord(Connection conn, int userId, int gameId, String targetWord, boolean resume) throws SQLException {
        List<String> previousGuesses = new ArrayList<>();
        int attemptsUsed = 0;

        if (resume) {
            // Load previous guesses
            PreparedStatement ps = conn.prepareStatement("SELECT guess_text, attempt_no FROM guesses WHERE game_id=? ORDER BY attempt_no");
            ps.setInt(1, gameId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                previousGuesses.add(rs.getString("guess_text"));
                attemptsUsed = rs.getInt("attempt_no");
            }

            if (!previousGuesses.isEmpty()) {
                System.out.println("Your previous guesses:");
                for (String g : previousGuesses) printColoredGuess(g, targetWord);
            }
        }

        for (int attempt = attemptsUsed + 1; attempt <= 5; attempt++) {
            System.out.print("Attempt " + attempt + " (5 letters, UPPERCASE, or OK to pause): ");
            String guess = sc.nextLine().toUpperCase();

            if (guess.equals("OK")) {
                // Pause the game
                PreparedStatement psPause = conn.prepareStatement("UPDATE games SET status='PAUSED', attempts_used=? WHERE game_id=?");
                psPause.setInt(1, attempt - 1);
                psPause.setInt(2, gameId);
                psPause.executeUpdate();
                return true; // stop further execution
            }

            if (guess.length() != 5) {
                System.out.println("? Invalid word length!");
                attempt--;
                continue;
            }

            // Save guess
            PreparedStatement psGuess = conn.prepareStatement("INSERT INTO guesses(game_id,guess_text,attempt_no) VALUES(?,?,?)");
            psGuess.setInt(1, gameId);
            psGuess.setString(2, guess);
            psGuess.setInt(3, attempt);
            psGuess.executeUpdate();

            previousGuesses.add(guess);

            System.out.println("Your guesses so far:");
            for (String g : previousGuesses) printColoredGuess(g, targetWord);

            if (guess.equals(targetWord)) {
                System.out.println("🎉 Correct! You guessed the word!");
                PreparedStatement psUpdate = conn.prepareStatement("UPDATE games SET correct=1, attempts_used=?, status='COMPLETED' WHERE game_id=?");
                psUpdate.setInt(1, attempt);
                psUpdate.setInt(2, gameId);
                psUpdate.executeUpdate();
                return false;
            }
        }

        // If word not guessed after 5 attempts
        System.out.println("? Word was: " + targetWord);
        PreparedStatement psUpdate = conn.prepareStatement("UPDATE games SET correct=0, attempts_used=5, status='FAILED' WHERE game_id=?");
        psUpdate.setInt(1, gameId);
        psUpdate.executeUpdate();
        return false;
    }

    private static void printColoredGuess(String guess, String targetWord) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < guess.length(); i++) {
            char c = guess.charAt(i);
            if (i < targetWord.length() && c == targetWord.charAt(i)) sb.append(GREEN).append(c).append(RESET);
            else if (targetWord.contains("" + c)) sb.append(YELLOW).append(c).append(RESET);
            else sb.append(GREY).append(c).append(RESET);
        }
        System.out.println(sb);
    }

    private static int getUserId(Connection conn, String username) throws SQLException {
        PreparedStatement ps = conn.prepareStatement("SELECT user_id FROM users WHERE username=?");
        ps.setString(1, username);
        ResultSet rs = ps.executeQuery();
        rs.next();
        return rs.getInt("user_id");
    }

    private static String getWordById(Connection conn, int wordId) throws SQLException {
        PreparedStatement ps = conn.prepareStatement("SELECT word_text FROM words WHERE id=?");
        ps.setInt(1, wordId);
        ResultSet rs = ps.executeQuery();
        rs.next();
        return rs.getString("word_text");
    }

    // ---------- Admin Menu ----------
    private static void adminMenu(Connection conn) throws SQLException {
        System.out.println("\nAdmin Menu:");
        System.out.println("1. Daily report");
        System.out.println("2. Per-user report");
        System.out.print("Choose option: ");
        int choice = Integer.parseInt(sc.nextLine());

        if (choice == 1) {
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT u.username, " +
                            "SUM(g.correct) AS correct_guesses, " +
                            "SUM(CASE WHEN g.correct=0 THEN 1 ELSE 0 END) AS wrong_guesses " +
                            "FROM games g " +
                            "JOIN users u ON g.user_id=u.user_id " +
                            "WHERE date_played >= datetime('now','start of day') " +
                            "GROUP BY u.user_id");
            ResultSet rs = ps.executeQuery();

            int userCount = 0;
            System.out.println("\n📊 Daily Report:");
            System.out.printf("%-15s %-15s %-15s\n", "Username", "Correct", "Wrong");
            while (rs.next()) {
                userCount++;
                System.out.printf("%-15s %-15d %-15d\n",
                        rs.getString("username"),
                        rs.getInt("correct_guesses"),
                        rs.getInt("wrong_guesses"));
            }
            System.out.println("Total users played today: " + userCount);

        } else if (choice == 2) {
            System.out.println("\nPer-user Report Options:");
            System.out.println("1. All users");
            System.out.println("2. Specific username");
            System.out.print("Choose option: ");
            int subChoice = Integer.parseInt(sc.nextLine());

            String query = "";
            if (subChoice == 1) {
                query = "SELECT u.username, u.password, g.game_id, g.date_played, w.word_text AS word, " +
                        "gu.guess_text, gu.attempt_no, g.correct, g.status " +
                        "FROM users u " +
                        "JOIN games g ON u.user_id=g.user_id " +
                        "JOIN words w ON g.word_id=w.id " +
                        "LEFT JOIN guesses gu ON g.game_id=gu.game_id " +
                        "ORDER BY u.username, g.date_played, g.game_id, gu.attempt_no";
                PreparedStatement ps = conn.prepareStatement(query);
                ResultSet rs = ps.executeQuery();
                printUserGameDetails(rs);
            } else if (subChoice == 2) {
                System.out.print("Enter username: ");
                String uname = sc.nextLine();
                query = "SELECT u.username, u.password, g.game_id, g.date_played, w.word_text AS word, " +
                        "gu.guess_text, gu.attempt_no, g.correct, g.status " +
                        "FROM users u " +
                        "JOIN games g ON u.user_id=g.user_id " +
                        "JOIN words w ON g.word_id=w.id " +
                        "LEFT JOIN guesses gu ON g.game_id=gu.game_id " +
                        "WHERE u.username=? " +
                        "ORDER BY g.date_played, g.game_id, gu.attempt_no";
                PreparedStatement ps = conn.prepareStatement(query);
                ps.setString(1, uname);
                ResultSet rs = ps.executeQuery();
                printUserGameDetails(rs);
            } else {
                System.out.println("Invalid option.");
            }

        } else {
            System.out.println("Invalid option.");
        }
    }

    // ---------- Helper function to print user game details ----------
    private static void printUserGameDetails(ResultSet rs) throws SQLException {
        String lastUser = "";
        String lastGame = "";

        while (rs.next()) {
            String username = rs.getString("username");
            String password = rs.getString("password");
            String gameId = rs.getString("game_id");
            String word = rs.getString("word");
            String guess = rs.getString("guess_text");
            int attempt = rs.getInt("attempt_no");
            boolean correct = rs.getBoolean("correct");
            String status = rs.getString("status");
            String date = rs.getString("date_played");

            if (!username.equals(lastUser)) {
                System.out.println("\nUsername: " + username + " | Password: *****");
                lastUser = username;
                lastGame = "";
            }

            if (!gameId.equals(lastGame)) {
                System.out.println("\nWord: " + word + " | Status: " + status + " | Correct: " + (correct ? "Yes" : "No") + " | Played at: " + date);
                lastGame = gameId;
            }

            if (guess != null) {
                printColoredGuessAdmin(guess, word);
            }
        }
    }

    // ---------- Colored guesses for admin ----------
    private static void printColoredGuessAdmin(String guess, String target) {
        for (int i = 0; i < guess.length(); i++) {
            char g = guess.charAt(i);
            if (i < target.length() && g == target.charAt(i)) System.out.print(GREEN + g + RESET);
            else if (target.indexOf(g) >= 0) System.out.print(YELLOW + g + RESET);
            else System.out.print(GREY + g + RESET);
        }
        System.out.println();
    }
}
