import type { CYP3A4Factor } from './types';

export const CYP3A4_FACTORS: Record<string, CYP3A4Factor> = {
  none: { name: 'No Interaction', factor: 1.0, desc: 'Normal metabolism' },
  inhibitor_mild: { name: 'Mild Inhibitor', factor: 0.85, desc: 'Grapefruit, Cimetidine' },
  inhibitor_moderate: { name: 'Moderate Inhibitor', factor: 0.70, desc: 'Fluconazole, Erythromycin' },
  inhibitor_strong: { name: 'Strong Inhibitor', factor: 0.50, desc: 'Ketoconazole, Ritonavir' },
  inducer_mild: { name: 'Mild Inducer', factor: 1.3, desc: 'Modafinil' },
  inducer_moderate: { name: 'Moderate Inducer', factor: 1.6, desc: 'Efavirenz, Nevirapine' },
  inducer_strong: { name: 'Strong Inducer', factor: 2.5, desc: 'Rifampin, Carbamazepine' }
};
