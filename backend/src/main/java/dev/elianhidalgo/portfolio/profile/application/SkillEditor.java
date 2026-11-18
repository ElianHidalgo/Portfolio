package dev.elianhidalgo.portfolio.profile.application;

import dev.elianhidalgo.portfolio.profile.domain.Skill;
import dev.elianhidalgo.portfolio.profile.domain.SkillCategory;
import dev.elianhidalgo.portfolio.profile.domain.SkillRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Adds, edits, removes and reorders the skills shown on the site. */
@Service
public class SkillEditor {

    private final SkillRepository skills;

    public SkillEditor(SkillRepository skills) {
        this.skills = skills;
    }

    @Transactional
    public Skill create(String name, SkillCategory category) {
        if (skills.existsByNameIgnoreCase(name)) {
            throw new DuplicateSkillNameException();
        }
        int next = skills.findFirstByOrderByDisplayOrderDesc().map(last -> last.getDisplayOrder() + 1).orElse(0);
        return flush(new Skill(name, category, next));
    }

    @Transactional
    public Skill update(long id, String name, SkillCategory category) {
        var skill = skills.findById(id).orElseThrow(SkillNotFoundException::new);
        if (skills.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new DuplicateSkillNameException();
        }
        skill.rename(name);
        if (skill.getCategory() != category) {
            int last = skills.findFirstByOrderByDisplayOrderDesc().map(Skill::getDisplayOrder).orElse(0);
            skill.moveTo(category);
            skill.placeAt(last + 1);
        }
        return flush(skill);
    }

    @Transactional
    public void delete(long id) {
        skills.delete(skills.findById(id).orElseThrow(SkillNotFoundException::new));
    }

    @Transactional
    public List<Skill> reorder(List<Long> ids) {
        List<Skill> all = skills.findAll();
        Map<Long, Skill> byId = all.stream().collect(Collectors.toMap(Skill::getId, Function.identity()));
        if (ids.size() != all.size() || !new HashSet<>(ids).equals(byId.keySet())) {
            throw new InvalidSkillOrderException();
        }
        for (int position = 0; position < ids.size(); position++) {
            byId.get(ids.get(position)).placeAt(position);
        }
        return ids.stream().map(byId::get).toList();
    }

    /** Two tabs may race past the existence check; the unique constraint then decides. */
    private Skill flush(Skill skill) {
        try {
            return skills.saveAndFlush(skill);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateSkillNameException();
        }
    }
}
