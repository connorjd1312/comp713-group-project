package nz.ac.aut.comp713.repair_tickets.controller;

import jakarta.validation.Valid;
import nz.ac.aut.comp713.repair_tickets.dto.CreateTicketRequest;
import nz.ac.aut.comp713.repair_tickets.dto.TicketResponse;
import nz.ac.aut.comp713.repair_tickets.dto.UpdateStatusRequest;
import nz.ac.aut.comp713.repair_tickets.model.TicketStatus;
import nz.ac.aut.comp713.repair_tickets.service.TicketService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/**
 * API 層：只負責 HTTP（網址、方法、狀態碼、JSON 轉換）。
 * 規則判斷全部交給 TicketService。
 */
@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService service;

    public TicketController(TicketService service) {
        this.service = service;
    }

    /** POST /api/tickets → 201 Created，Location 標頭指向新工單。 */
    @PostMapping
    public ResponseEntity<TicketResponse> create(@Valid @RequestBody CreateTicketRequest req) {
        TicketResponse created = service.createTicket(req.name(), req.email(), req.phone(),
                req.deviceType(), req.deviceModel(), req.issueDescription());
        return ResponseEntity.created(URI.create("/api/tickets/" + created.id())).body(created);
    }

    /** GET /api/tickets?status=REPAIRING&email=a@b.com → 200，兩個篩選條件都可省略。 */
    @GetMapping
    public List<TicketResponse> list(@RequestParam(required = false) TicketStatus status,
                                     @RequestParam(required = false) String email) {
        return service.listTickets(status, email);
    }

    /** GET /api/tickets/{id} → 200，找不到回 404。 */
    @GetMapping("/{id}")
    public TicketResponse get(@PathVariable Long id) {
        return service.getTicket(id);
    }

    /** PATCH /api/tickets/{id}/status → 200，跳步回 409。 */
    @PatchMapping("/{id}/status")
    public TicketResponse updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateStatusRequest req) {
        return service.updateStatus(id, req.status(), req.note());
    }

    /** DELETE /api/tickets/{id} → 204，取消工單（保留紀錄）；已開始處理回 409。 */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancel(@PathVariable Long id) {
        service.cancelTicket(id);
        return ResponseEntity.noContent().build();
    }
}
