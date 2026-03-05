import type { DrugInfo } from '../pkpd/types';

export interface EnzymeInteraction {
  enzyme: string;
  Ki: number;
  IC50: number;
  hillCoef: number;
  mechanism: string;
}

export interface DrugWithInteraction extends DrugInfo {
  cyp3a4?: boolean;
  isProgestogen?: boolean;
  hillEnzyme?: EnzymeInteraction;
}

export interface CYP3A4Factor {
  name: string;
  factor: number;
  desc: string;
}
