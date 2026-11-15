import { groupSkills } from './group-skills';

describe('groupSkills', () => {
  it('groups skills in the fixed category order and omits empty groups', () => {
    const groups = groupSkills([
      { name: 'Docker', category: 'DEVOPS' },
      { name: 'Java', category: 'BACKEND' },
      { name: 'Angular', category: 'FRONTEND' },
      { name: 'Spring Boot', category: 'BACKEND' },
    ]);

    expect(groups.map((g) => g.category)).toEqual(['BACKEND', 'FRONTEND', 'DEVOPS']);
    expect(groups[0].skills.map((s) => s.name)).toEqual(['Java', 'Spring Boot']);
    expect(groups[2].label).toBe('DevOps');
  });

  it('returns no groups for no skills', () => {
    expect(groupSkills([])).toEqual([]);
  });
});
