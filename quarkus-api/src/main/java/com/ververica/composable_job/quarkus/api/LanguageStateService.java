package com.ververica.composable_job.quarkus.api;

import com.ververica.composable_job.model.Language;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class LanguageStateService {
    private final List<Language> languages = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, Language> preferredLanguageByUser = Collections.synchronizedMap(new HashMap<>());

    public void setUserLanguage(String user, Language language) {
        if (Language.DEFAULT.equals(language)) {
            return;
        }

        preferredLanguageByUser.put(user, language);

        addLanguage(language);
    }

    public Language getUserLanguage(String user) {
        return preferredLanguageByUser.getOrDefault(user, Language.DEFAULT);
    }

    public List<Language> getLanguages() {
        return languages;
    }

    private void addLanguage(Language language) {
        if (languages.contains(language)) {
            Log.info("Language already being translated to: " + language);
            return;
        }

        languages.add(language);
        Log.info("Added language: " + language);
    }
}
