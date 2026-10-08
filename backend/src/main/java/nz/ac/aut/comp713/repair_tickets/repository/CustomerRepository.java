package nz.ac.aut.comp713.repair_tickets.repository;

import nz.ac.aut.comp713.repair_tickets.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Optional<Customer> findByEmailIgnoreCase(String email);
}
