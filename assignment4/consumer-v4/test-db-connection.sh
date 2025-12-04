#!/bin/bash

# ============================================
# Simple Database Connection Test
# Tests if Consumer-v3 can connect to PostgreSQL
# ============================================

echo "=== Testing Database Connection ==="
echo ""

# Set environment variables
export DB_JDBC_URL="jdbc:postgresql://cs6650-chat-db.cr6q6mmc0zok.us-west-2.rds.amazonaws.com:5432/chatdb"
export DB_USERNAME="postgres"
export DB_PASSWORD="MyPassword123"

# Create a simple test Java file
cat > TestConnection.java << 'EOF'
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class TestConnection {
    public static void main(String[] args) {
        String jdbcUrl = System.getenv("DB_JDBC_URL");
        String username = System.getenv("DB_USERNAME");
        String password = System.getenv("DB_PASSWORD");

        System.out.println("Testing connection to: " + jdbcUrl);
        System.out.println("Username: " + username);
        System.out.println("");

        try {
            // Load PostgreSQL driver
            Class.forName("org.postgresql.Driver");

            // Establish connection
            System.out.println("Connecting...");
            Connection conn = DriverManager.getConnection(jdbcUrl, username, password);
            System.out.println("✓ Connection successful!");
            System.out.println("");

            // Test query
            System.out.println("Testing query...");
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT version()");

            if (rs.next()) {
                System.out.println("✓ Query successful!");
                System.out.println("Database version: " + rs.getString(1));
            }

            // Count messages
            System.out.println("");
            System.out.println("Checking messages table...");
            rs = stmt.executeQuery("SELECT COUNT(*) FROM messages");
            if (rs.next()) {
                System.out.println("✓ Messages table accessible!");
                System.out.println("Total messages: " + rs.getLong(1));
            }

            // Close connections
            rs.close();
            stmt.close();
            conn.close();

            System.out.println("");
            System.out.println("=== Test Passed ===");

        } catch (Exception e) {
            System.err.println("✗ Test Failed!");
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
EOF

# Compile
echo "Compiling test..."
javac -cp "target/lib/postgresql-42.7.3.jar:." TestConnection.java

if [ $? -ne 0 ]; then
    echo "✗ Compilation failed"
    exit 1
fi

# Run
echo ""
java -cp "target/lib/postgresql-42.7.3.jar:." TestConnection

# Cleanup
rm -f TestConnection.java TestConnection.class

echo ""
echo "Test complete!"
