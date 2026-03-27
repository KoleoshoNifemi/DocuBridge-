package Group12;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {
    private String connectionUrl;

    public DatabaseManager(String connectionUrl) {
        this.connectionUrl = connectionUrl;
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(connectionUrl);
    }

    public boolean testConnection() {
        try (Connection conn = getConnection()) {
            return conn.isValid(2);
        } catch (SQLException e) {
            return false;
        }
    }

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

    // ===== FILE MANAGEMENT =====

    public int createFile(int userId, String fileName) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO Files (user_id, name, content) VALUES (?, ?, ?)",
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

    public void deleteFile(int userId, String fileName) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "DELETE FROM Files WHERE user_id = ? AND name = ?")) {
            stmt.setInt(1, userId);
            stmt.setString(2, fileName);
            stmt.executeUpdate();
            System.out.println("✓ File deleted from database: " + fileName);
        } catch (SQLException e) {
            System.err.println("Error deleting file: " + e.getMessage());
        }
    }
}