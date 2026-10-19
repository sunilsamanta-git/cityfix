package com.cityfix.config;

import com.cityfix.model.*;
import com.cityfix.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/** Creates departments and demo accounts on first start so the app is usable immediately. */
@Component
public class DataSeeder implements CommandLineRunner {
    private final DepartmentRepository departments;
    private final UserRepository users;
    private final PasswordEncoder encoder;

    public DataSeeder(DepartmentRepository departments, UserRepository users, PasswordEncoder encoder) {
        this.departments = departments;
        this.users = users;
        this.encoder = encoder;
    }

    @Override
    public void run(String... args) {
        if (departments.count() > 0) return;

        List<String> names = List.of("Roads & Infrastructure", "Electrical", "Water Supply", "Sanitation");
        names.forEach(n -> departments.save(new Department(n)));

        user("City Admin", "admin@cityfix.com", "Admin@123", Role.ADMIN, null);
        user("Ravi (Roads)", "officer.roads@cityfix.com", "Officer@123", Role.OFFICER, departments.findByName("Roads & Infrastructure").orElseThrow());
        user("Meera (Water)", "officer.water@cityfix.com", "Officer@123", Role.OFFICER, departments.findByName("Water Supply").orElseThrow());
        user("Demo Citizen", "citizen@cityfix.com", "Citizen@123", Role.CITIZEN, null);
    }

    private void user(String name, String email, String password, Role role, Department dept) {
        User u = new User();
        u.setName(name);
        u.setEmail(email);
        u.setPassword(encoder.encode(password));
        u.setRole(role);
        u.setDepartment(dept);
        users.save(u);
    }
}
