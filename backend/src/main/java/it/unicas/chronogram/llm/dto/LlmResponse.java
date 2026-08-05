package it.unicas.chronogram.llm.dto;

/**
 * Structured activity fields extracted from a natural-language prompt.
 * Field names match the legacy {@code llmDTO} for front-end compatibility.
 */
public record LlmResponse(
        String name,
        Integer durationMins,
        String details,
        Integer pleasantness,
        Integer activityTypeId,
        String recurrence,
        String costEuro,
        String location
) {
    public static LlmResponse empty() {
        return new LlmResponse(null, null, null, null, null, null, null, null);
    }

    /**
     * Vero quando il modello non ha estratto alcun campo utile.
     * <p>
     * Serve a distinguere, nei log e per chi legge la risposta, l'estrazione
     * realmente vuota — il provider ha risposto ma la frase non conteneva dati
     * riconoscibili, quindi all'utente si chiede di riformulare — dal guasto
     * tecnico, che non produce mai un {@code LlmResponse} ma un errore HTTP.
     * Il confronto e' su stringa vuota oltre che su null perche' il parsing
     * normalizza {@code costEuro} assente a {@code ""}.
     */
    public boolean isEmpty() {
        return isBlank(name)
                && durationMins == null
                && isBlank(details)
                && pleasantness == null
                && activityTypeId == null
                && isBlank(recurrence)
                && isBlank(costEuro)
                && isBlank(location);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
