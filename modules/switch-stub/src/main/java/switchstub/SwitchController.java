package switchstub;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stub del switch. Se supone que la logica de prender y apagar va aca
 */
@RestController
public class SwitchController {

    private static final Logger log = LoggerFactory.getLogger(SwitchController.class);

    @PostMapping("/switch")
    public ResponseEntity<Void> accionar(@Valid @RequestBody SwitchAccionRequest request) {
        // Esta línea es la que "prende/apaga" el switch en este stub: solo consola.
        log.info("Switch {} -> {}", request.switchId(), request.accion());
        return ResponseEntity.ok().build();
    }
}
