import type { DrugDatabase } from './types';

export const DRUG_DB: DrugDatabase = {
  E2_oral: {
    name: 'Estradiol Oral',
    therapeutic: [50, 200], unit: 'pg/mL',
    CL: 60, Vd: 60, ka: 0.04, F: 0.03,
    halfLife: 1, halfLifeApparent: 17,
    doseUnit: 'mg', intervalUnit: 'h',
    defaultDose: 2, defaultInterval: 12,
    ref: 'PMID: 1548642'
  },
  E2V_oral: {
    name: 'Estradiol Valerate Oral',
    therapeutic: [50, 200], unit: 'pg/mL',
    CL: 60, Vd: 60, ka: 0.04, F: 0.03,
    halfLife: 1, halfLifeApparent: 17,
    doseUnit: 'mg', intervalUnit: 'h',
    defaultDose: 2, defaultInterval: 12,
    ref: 'PMID: 1548642'
  },
  E2_subl: {
    name: 'Estradiol Sublingual',
    therapeutic: [50, 200], unit: 'pg/mL',
    CL: 15, Vd: 150, ka: 2.5, F: 0.10,
    halfLife: 12,
    doseUnit: 'mg', intervalUnit: 'h',
    defaultDose: 2, defaultInterval: 12,
    ref: 'PMID: 9052581'
  },
  E2_td: {
    name: 'Estradiol Transdermal Patch',
    therapeutic: [50, 200], unit: 'pg/mL',
    CL: 10, Vd: 150, ka: 0.03, F: 0.85,
    halfLife: 36,
    doseUnit: 'mg', intervalUnit: 'h',
    defaultDose: 0.05, defaultInterval: 84,
    ref: 'PMID: 9689205'
  },
  E2_td_gel: {
    name: 'Estradiol Transdermal Gel',
    therapeutic: [50, 200], unit: 'pg/mL',
    CL: 12, Vd: 180, ka: 0.15, F: 0.10,
    halfLife: 24,
    doseUnit: 'mg', intervalUnit: 'h',
    defaultDose: 1.5, defaultInterval: 24,
    ref: 'PMID: 17143811'
  },
  E2V: {
    name: 'Estradiol Valerate IM',
    therapeutic: [100, 400], unit: 'pg/mL',
    CL: 100, Vd: 2400, ka: 0.012, F: 0.85,
    halfLife: 120,
    doseUnit: 'mg', intervalUnit: 'h',
    defaultDose: 5, defaultInterval: 168,
    ref: 'PMID: 7169965'
  },
  E2C: {
    name: 'Estradiol Cypionate IM',
    therapeutic: [100, 400], unit: 'pg/mL',
    CL: 80, Vd: 3000, ka: 0.008, F: 0.90,
    halfLife: 192,
    doseUnit: 'mg', intervalUnit: 'h',
    defaultDose: 5, defaultInterval: 336,
    ref: 'PMID: 28838353'
  },
  EEn: {
    name: 'Estradiol Enanthate IM',
    therapeutic: [100, 400], unit: 'pg/mL',
    CL: 90, Vd: 2700, ka: 0.010, F: 0.88,
    halfLife: 168,
    doseUnit: 'mg', intervalUnit: 'h',
    defaultDose: 5, defaultInterval: 168,
    ref: 'PMID: 28838353'
  },
  MPA_oral: {
    name: 'Medroxyprogesterone Acetate Oral',
    therapeutic: [1, 10], unit: 'ng/mL',
    CL: 50, Vd: 200, ka: 1.5, F: 0.90,
    halfLife: 24,
    doseUnit: 'mg', intervalUnit: 'h',
    defaultDose: 5, defaultInterval: 24,
    ref: 'PMID: 6336623'
  },
  CPA_oral: {
    name: 'Cyproterone Acetate Oral',
    therapeutic: [50, 300], unit: 'ng/mL',
    CL: 3.5, Vd: 350, ka: 0.5, F: 0.85,
    halfLife: 48,
    doseUnit: 'mg', intervalUnit: 'h',
    defaultDose: 12.5, defaultInterval: 24,
    ref: 'PMID: 3127499'
  },
  TEST_En: {
    name: 'Testosterone Enanthate IM',
    therapeutic: [300, 1000], unit: 'ng/dL',
    CL: 50, Vd: 1500, ka: 0.015, F: 0.95,
    halfLife: 120,
    doseUnit: 'mg', intervalUnit: 'h',
    defaultDose: 100, defaultInterval: 168,
    ref: 'PMID: 15476439'
  },
  TEST_Cy: {
    name: 'Testosterone Cypionate IM',
    therapeutic: [300, 1000], unit: 'ng/dL',
    CL: 45, Vd: 1600, ka: 0.013, F: 0.95,
    halfLife: 144,
    doseUnit: 'mg', intervalUnit: 'h',
    defaultDose: 100, defaultInterval: 168,
    ref: 'PMID: 15476439'
  }
};
