package br.com.reservas.shared.system;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Rota pública (D-40): faixa "Horário simulado" do front consome `/system/clock`. */
@RestController
@RequestMapping("/api/v1/system")
public class SystemController {

    private final SystemClockService clockService;

    public SystemController(SystemClockService clockService) {
        this.clockService = clockService;
    }

    @GetMapping("/clock")
    public ClockResponse clock() {
        return clockService.currentClock();
    }
}
