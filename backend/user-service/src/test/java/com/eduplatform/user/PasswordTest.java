import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordTest {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String password = "123456";
        String hash = encoder.encode(password);
        System.out.println("Generated Hash: " + hash);
        System.out.println("Matches: " + encoder.matches(password, hash));

        String dbHash = "$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xd00DMxs.p.Y8GVC";
        System.out.println("Matches DB Hash: " + encoder.matches(password, dbHash));
    }
}
