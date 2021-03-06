package io.onedev.server.manager.impl;

import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.hibernate.criterion.Restrictions;

import io.onedev.server.manager.CommitQuerySettingManager;
import io.onedev.server.model.CommitQuerySetting;
import io.onedev.server.model.Project;
import io.onedev.server.model.User;
import io.onedev.server.persistence.annotation.Sessional;
import io.onedev.server.persistence.annotation.Transactional;
import io.onedev.server.persistence.dao.AbstractEntityManager;
import io.onedev.server.persistence.dao.Dao;
import io.onedev.server.persistence.dao.EntityCriteria;

@Singleton
public class DefaultCommitQuerySettingManager extends AbstractEntityManager<CommitQuerySetting> 
		implements CommitQuerySettingManager {

	@Inject
	public DefaultCommitQuerySettingManager(Dao dao) {
		super(dao);
	}

	@Sessional
	@Override
	public CommitQuerySetting find(Project project, User user) {
		EntityCriteria<CommitQuerySetting> criteria = newCriteria();
		criteria.add(Restrictions.and(Restrictions.eq("project", project), Restrictions.eq("user", user)));
		return find(criteria);
	}

	@Transactional
	@Override
	public void save(CommitQuerySetting setting) {
		setting.getQuerySubscriptionSupport().getUserQuerySubscriptions().retainAll(
				setting.getUserQueries().stream().map(it->it.getName()).collect(Collectors.toSet()));
		setting.getQuerySubscriptionSupport().getProjectQuerySubscriptions().retainAll(
				setting.getProject().getSavedCommitQueries().stream().map(it->it.getName()).collect(Collectors.toSet()));
		if (setting.getQuerySubscriptionSupport().getProjectQuerySubscriptions().isEmpty() && setting.getUserQueries().isEmpty()) {
			if (!setting.isNew())
				delete(setting);
		} else {
			super.save(setting);
		}
	}

}
