import type { DrugWithInteraction } from './types';

export const PROGESTOGENS: Record<string, DrugWithInteraction> = {
  MPA_oral: {
    name: 'Medroxyprogesterone Acetate Oral',
    therapeutic: [0.5, 3],
    unit: 'ng/mL',
    CL: 20,
    Vd: 35,
    ka: 1.2,
    F: 0.95,
    halfLife: 30,
    doseUnit: 'mg',
    intervalUnit: 'h',
    defaultDose: 10,
    defaultInterval: 24,
    cyp3a4: true,
    isProgestogen: true,
    hillEnzyme: {
      enzyme: 'CYP3A4',
      Ki: 2.5,
      IC50: 5.0,
      hillCoef: 1.0,
      mechanism: 'substrate_weak_inhibitor'
    },
    ref: 'DrugBank DB00603'
  },
  CPA_oral: {
    name: 'Cyproterone Acetate Oral',
    therapeutic: [20, 300],
    unit: 'ng/mL',
    CL: 5,
    Vd: 3,
    ka: 0.8,
    F: 0.88,
    halfLife: 60,
    doseUnit: 'mg',
    intervalUnit: 'h',
    defaultDose: 25,
    defaultInterval: 24,
    cyp3a4: true,
    isProgestogen: true,
    hillEnzyme: {
      enzyme: 'CYP3A4',
      Ki: 0.8,
      IC50: 1.5,
      hillCoef: 1.2,
      mechanism: 'substrate_moderate_inhibitor'
    },
    ref: 'PMID: 8131397 | DrugBank DB04839'
  }
};

export const ANDROGENS: Record<string, DrugWithInteraction> = {
  TEST_En: {
    name: 'Testosterone Enanthate IM',
    therapeutic: [300, 1000],
    unit: 'ng/dL',
    CL: 80,
    Vd: 1900,
    ka: 0.015,
    F: 0.65,
    halfLife: 96,
    doseUnit: 'mg',
    intervalUnit: 'h',
    defaultDose: 100,
    defaultInterval: 168,
    ref: 'PMC4721027 | PMC9293229'
  },
  TEST_Cy: {
    name: 'Testosterone Cypionate IM',
    therapeutic: [300, 1000],
    unit: 'ng/dL',
    CL: 6,
    Vd: 900,
    ka: 0.012,
    F: 0.65,
    halfLife: 120,
    doseUnit: 'mg',
    intervalUnit: 'h',
    defaultDose: 100,
    defaultInterval: 168,
    ref: 'Clinical data'
  }
};
