package com.shale.ui.component.factory;

import java.util.Objects;
import java.util.function.Consumer;

import com.shale.ui.component.UserCard;
import com.shale.ui.util.ColorUtil;

public class UserCardFactory {

	public enum Variant {
		FULL, COMPACT, MINI, TEAM_ACCENTED
	}

	public record UserCardModel(
			Integer userId,
			String displayName,
			String colorCss,
			String avatarRef
	) {
	}

	private final Consumer<Integer> onOpenUser;

	public UserCardFactory(Consumer<Integer> onOpenUser) {
		this.onOpenUser = onOpenUser;
	}

	public UserCard create(UserCardModel model, Variant variant) {
		Objects.requireNonNull(model, "model");

		UserCard card = new UserCard();
		card.setUserId(model.userId());
		card.setOnOpen(onOpenUser);

		card.setName(model.displayName());
		card.setBackgroundCssColor(ColorUtil.toCssBackgroundColorOrNull(model.colorCss()));

		switch (variant) {
		case FULL -> card.applyFull();
		case COMPACT -> card.applyCompact();
		case MINI -> card.applyMini();
		case TEAM_ACCENTED -> {
			card.applyFull();
			card.applyTeamAccent(model.colorCss());
		}
		}

		return card;
	}
}
