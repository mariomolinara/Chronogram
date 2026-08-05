import { defineConfig } from 'cypress';

export default defineConfig({
  e2e: {
    supportFile: 'tests/e2e/support/e2e.{js,jsx,ts,tsx}',
    specPattern: 'tests/e2e/specs/**/*.cy.{js,jsx,ts,tsx}',
    videosFolder: 'tests/e2e/videos',
    screenshotsFolder: 'tests/e2e/screenshots',
    baseUrl: 'http://localhost:5173',
    // Le pagine hanno un header Ionic translucido e il contenuto ci scorre sotto:
    // con lo scroll predefinito ("top") Cypress porta l'elemento proprio dietro
    // la toolbar e lo considera coperto. Centrandolo resta sempre cliccabile.
    scrollBehavior: 'center',
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    setupNodeEvents(on, config) {
      // implement node event listeners here
    },
  },
});
