import { groupSkills } from './group-skills';

describe('groupSkills', () => {
  it('groups skills in the fixed category order and omits empty groups', () => {
    const groups = groupSkills([
      { id: 1, name: 'Docker', category: 'DEVOPS' },
      { id: 2, name: 'Java', category: 'BACKEND' },
      { id: 3, name: 'Angular', category: 'FRONTEND' },
      { id: 4, name: 'Spring Boot', category: 'BACKEND' },
    ]);

    expect(groups.map((g) => g.category)).toEqual(['BACKEND', 'FRONTEND', 'DEVOPS']);
    expect(groups[0].skills.map((s) => s.name)).toEqual(['Java', 'Spring Boot']);
    expect(groups[2].label).toBe('DevOps');
  });

  it('returns no groups for no skills', () => {
    expect(groupSkills([])).toEqual([]);
  });
});
