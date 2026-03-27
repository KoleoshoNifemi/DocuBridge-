package Group12;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {
    //full JDBC connection string, e.g. jdbc:sqlserver://host;databaseName=...
    private String connectionUrl;

    public DatabaseManager(String connectionUrl) {
        this.connectionUrl = connectionUrl;
    }

    //opens a fresh connection each call; callers are expected to close it (try-with-resources)
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(connectionUrl);
    }

    //quick ping to check if the DB is reachable; 2s timeout so it doesn't hang the UI thread
    public boolean testConnection() {
        try (Connection conn = getConnection()) {
            return conn.isValid(2);
        } catch (SQLException e) {
            return false;
        }
    }

    //returns false if the username already exists (unique constraint violation) or any other SQL error
    public boolean registerUser(String username, String passwordHash) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement("INSERT INTO Users (username, password_hash) VALUES (?, ?)")) {
            stmt.setString(1, username);
            stmt.setString(2, passwordHash);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("Registration error: " + e.getMessage());
            return false;
        }
    }

    //returns null if the username doesn't exist; caller must handle the null before calling BCrypt
    public String getPasswordHash(String username) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT password_hash FROM Users WHERE username = ?")) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("password_hash");
            }
            return null;
        } catch (SQLException e) {
            System.err.println("Query error: " + e.getMessage());
            return null;
        }
    }

    //returns -1 if the user isn't found; matches the sentinel value used in AuthenticationUI
    public int getUserId(String username) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT id FROM Users WHERE username = ?")) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt("id");
        } catch (SQLException e) {
            System.err.println("Query error: " + e.getMessage());
        }
        return -1;
    }

    //file operations

    //creates the file with empty content and returns the generated DB id, or -1 on failure
    public int createFile(int userId, String fileName) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO Files (user_id, name, content) VALUES (?, ?, ?)",
                     //need RETURN_GENERATED_KEYS so we can hand the caller the new file's id
                     Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, userId);
            stmt.setString(2, fileName);
            stmt.setString(3, "");
            stmt.executeUpdate();

            ResultSet rs = stmt.getGeneratedKeys();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.err.println("Create file error: " + e.getMessage());
        }
        return -1;
    }

    //returns file names sorted newest-first; scoped to the user so they can't see each other's files
    public List<String> getUserFiles(int userId) {
        List<String> files = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT name FROM Files WHERE user_id = ? ORDER BY updated_at DESC")) {
            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                files.add(rs.getString("name"));
            }
        } catch (SQLException e) {
            System.err.println("List files error: " + e.getMessage());
        }
        return files;
    }

    //guards against a null content column; returns empty string so callers don't have to null-check
    public String getFileContent(int userId, String fileName) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT content FROM Files WHERE user_id = ? AND name = ?")) {
            stmt.setInt(1, userId);
            stmt.setString(2, fileName);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String content = rs.getString("content");
                return content != null ? content : "";
            }
        } catch (SQLException e) {
            System.err.println("Retrieve file error: " + e.getMessage());
        }
        return "";
    }

    //GETDATE() is SQL Server syntax; also updates updated_at so getUserFiles ordering stays correct
    public void saveFileContent(int userId, String fileName, String content) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "UPDATE Files SET content = ?, updated_at = GETDATE() WHERE user_id = ? AND name = ?")) {
            stmt.setString(1, content);
            stmt.setInt(2, userId);
            stmt.setString(3, fileName);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Save error: " + e.getMessage());
        }
    }

    //scoped by userId to prevent one user from deleting another's files
    public void deleteFile(int userId, String fileName) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "DELETE FROM Files WHERE user_id = ? AND name = ?")) {
            stmt.setInt(1, userId);
            stmt.setString(2, fileName);
            stmt.executeUpdate();
            //System.out.println("✓ File deleted from database: " + fileName);
        } catch (SQLException e) {
            System.err.println("Error deleting file: " + e.getMessage());
        }
    }
}
