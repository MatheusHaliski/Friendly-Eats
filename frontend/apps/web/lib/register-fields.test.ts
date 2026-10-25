import { describe, expect, it } from 'vitest';
import { buildTypeDetails, fieldsFor } from './register-fields';

const keys = (type: Parameters<typeof fieldsFor>[0], informal = false, minor = false) => fieldsFor(type, informal, minor).map((f) => f.key);
const required = (type: Parameters<typeof fieldsFor>[0], informal = false) =>
  fieldsFor(type, informal, false)
    .filter((f) => f.required)
    .map((f) => f.key)
    .sort();

// mesmas listas do UserTypeDetailsValidator.java
describe('campos por tipo de conta', () => {
  it('obrigatórios iguais aos do backend', () => {
    expect(required('CRIADOR')).toEqual(['channelName', 'format', 'website']);
    expect(required('CHEF')).toEqual(['role']);
    expect(required('ESTABELECIMENTO')).toEqual(
      ['cnpj', 'legalName', 'tradeName', 'cnae', 'city', 'state', 'representativeName', 'representativeRole', 'representativeEmail'].sort(),
    );
    expect(required('ESTABELECIMENTO', true)).toEqual(['city', 'cpf', 'informalFormat', 'state', 'tradeName']);
    expect(required('VAREJO')).toEqual(['cnae', 'cnpj', 'legalName', 'representativeEmail', 'representativeName', 'representativeRole', 'segment']);
    expect(required('COMUNIDADE')).toEqual(['city', 'communityType', 'contactEmail', 'contactName', 'state']);
    expect(required('OFICIAL')).toEqual(['agency', 'cnpj', 'legalName', 'representativeEmail', 'representativeName', 'representativeRole', 'website']);
  });

  it('negócio sem CNPJ não manda CNPJ, razão social, CNAE nem representante', () => {
    expect(keys('ESTABELECIMENTO', true)).not.toEqual(expect.arrayContaining(['cnpj']));
    expect(keys('ESTABELECIMENTO', true)).toEqual(['informalFormat', 'cpf', 'tradeName', 'city', 'state', 'website']);
  });

  it('e-mail do responsável só para comensal menor de 16', () => {
    expect(keys('COMENSAL', false, true)).toContain('guardianEmail');
    expect(keys('COMENSAL')).not.toContain('guardianEmail');
  });

  it('type_details só com as chaves do tipo e sem vazios', () => {
    const fields = fieldsFor('ESTABELECIMENTO', true, false);
    expect(buildTypeDetails(fields, { informalFormat: 'FOOD_TRUCK', cpf: ' 52998224725 ', city: '', cnpj: '123', tradeName: 'Truck' })).toEqual({
      informalFormat: 'FOOD_TRUCK',
      cpf: '52998224725',
      tradeName: 'Truck',
    });
  });
});
