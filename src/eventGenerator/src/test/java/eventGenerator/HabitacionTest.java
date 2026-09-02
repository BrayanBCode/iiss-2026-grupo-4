package eventGenerator;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias de Habitacion (eventGenerator).
 *
 * El MqttClient se mockea con Mockito: no se necesita un broker real
 * (mosquitto) corriendo para ejecutar estos tests.
 */
@ExtendWith(MockitoExtension.class)
class HabitacionTest {

    private static final String DEVICE_ID = "shellyhtg3-a1b2c3d4e5f6";

    @Mock
    private MqttClient mqttClientMock;

    private Habitacion habitacion;

    @BeforeEach
    void setUp() {
        habitacion = new Habitacion(DEVICE_ID, mqttClientMock);
    }

    @Test
    void generarEvento_publicaEnElTopicCorrecto() throws MqttException {
        habitacion.generarEvento();

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        verify(mqttClientMock).publish(topicCaptor.capture(), any(MqttMessage.class));

        assertEquals(DEVICE_ID + "/status/temperature:0", topicCaptor.getValue());
    }

    @Test
    void generarEvento_publicaUnPayloadJsonConLosCamposEsperados() throws MqttException {
        habitacion.generarEvento();

        ArgumentCaptor<MqttMessage> mensajeCaptor = ArgumentCaptor.forClass(MqttMessage.class);
        verify(mqttClientMock).publish(anyString(), mensajeCaptor.capture());

        JSONObject payload = new JSONObject(new String(mensajeCaptor.getValue().getPayload()));

        // El id del componente sensor siempre es 0 para el H&T Gen3
        assertEquals(0, payload.getInt("id"));
        assertTrue(payload.has("tC"));
        assertTrue(payload.has("tF"));
        assertTrue(payload.has("ts"));

        double tC = payload.getDouble("tC");
        double tF = payload.getDouble("tF");
        double ts = payload.getDouble("ts");

        // tF debe ser la conversión correcta de tC. OJO: tanto tC como tF vienen
        // redondeados a 1 decimal en el payload (%.1f), y cada uno se redondeó por
        // separado a partir del valor interno real (sin redondear). Por eso la
        // tolerancia no puede ser 0.05: hay que contemplar el redondeo de tC (hasta
        // 0.05 de error) propagado a través de la fórmula (*9/5) más el propio
        // redondeo de tF (hasta 0.05 más).
        double tFEsperada = (tC * 9.0 / 5.0) + 32.0;
        assertEquals(tFEsperada, tF, 0.2);

        // El timestamp debe ser un epoch en segundos razonable (cercano a "ahora")
        double ahora = System.currentTimeMillis() / 1000.0;
        assertEquals(ahora, ts, 5.0);
    }

    @Test
    void generarEvento_primeraLecturaEstaCercaDeLaTemperaturaInicial() throws MqttException {
        habitacion.generarEvento();

        JSONObject payload = capturarUltimoPayload();
        double tC = payload.getDouble("tC");

        // La temperatura arranca en 21.0 y cada llamada la mueve como máximo +-0.5
        assertEquals(21.0, tC, 0.5);
    }

    @Test
    void generarEvento_llamadasSucesivasVarianDentroDelRangoEsperado() throws MqttException {
        double anterior = 21.0;

        for (int i = 0; i < 20; i++) {
            reset(mqttClientMock);
            habitacion.generarEvento();

            double actual = capturarUltimoPayload().getDouble("tC");
            double delta = actual - anterior;

            // generarEvento() suma (Math.random() - 0.5), o sea un valor en [-0.5, 0.5).
            // Como comparamos valores YA redondeados a 1 decimal (%.1f) en vez del
            // double interno real, cada lectura puede desviarse hasta 0.05 del valor
            // real por el redondeo — así que damos un margen de 0.05 de cada lado.
            assertTrue(delta >= -0.55 && delta <= 0.55,
                    "Delta fuera de rango: " + delta);

            anterior = actual;
        }
    }

    @Test
    void generarEvento_sePropagaLaMqttExceptionSiFallaElPublish() throws MqttException {
        doThrow(new MqttException(MqttException.REASON_CODE_CLIENT_NOT_CONNECTED))
                .when(mqttClientMock).publish(anyString(), any(MqttMessage.class));

        assertThrows(MqttException.class, () -> habitacion.generarEvento());
    }

    private JSONObject capturarUltimoPayload() throws MqttException {
        ArgumentCaptor<MqttMessage> mensajeCaptor = ArgumentCaptor.forClass(MqttMessage.class);
        verify(mqttClientMock, atLeastOnce()).publish(anyString(), mensajeCaptor.capture());
        MqttMessage ultimo = mensajeCaptor.getValue();
        return new JSONObject(new String(ultimo.getPayload()));
    }
}