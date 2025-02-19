/*
 * Copyright (C) 2020  Haowei Wen <yushijinhun@gmail.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package moe.yushi.authlibinjector.httpd;

import static java.util.Optional.empty;
import static moe.yushi.authlibinjector.util.UUIDUtils.fromUnsignedUUID;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import moe.yushi.authlibinjector.internal.fi.iki.elonen.IHTTPSession;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.Response;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.Status;
import moe.yushi.authlibinjector.yggdrasil.GameProfile;
import moe.yushi.authlibinjector.yggdrasil.YggdrasilClient;
import moe.yushi.authlibinjector.yggdrasil.YggdrasilResponseBuilder;

public class QueryProfileFilter implements URLFilter {

	private static final Pattern PATH_REGEX = Pattern.compile("^/session/minecraft/profile/(?<uuid>[0-9a-f]{32})$");

	private YggdrasilClient mojangClient;
	private YggdrasilClient customClient;

	public QueryProfileFilter(YggdrasilClient mojangClient, YggdrasilClient customClient) {
		this.mojangClient = mojangClient;
		this.customClient = customClient;
	}

	@Override
	public boolean canHandle(String domain) {
		return domain.equals("sessionserver.mojang.com");
	}

	@Override
	public Optional<Response> handle(String domain, String path, IHTTPSession session) throws IOException {
		if (!domain.equals("sessionserver.mojang.com"))
			return empty();
		Matcher matcher = PATH_REGEX.matcher(path);
		if (!matcher.find())
			return empty();

		UUID uuid;
		try {
			uuid = fromUnsignedUUID(matcher.group("uuid"));
		} catch (IllegalArgumentException e) {
			uuid = java.util.UUID.randomUUID();
		}

		boolean withSignature = false;
		List<String> unsignedValues = session.getParameters().get("unsigned");
		if (unsignedValues != null && unsignedValues.get(0).equals("false")) {
			withSignature = true;
		}

		Optional<GameProfile> response;
		if (QueryUUIDsFilter.isMaskedUUID(uuid)) {
			response = mojangClient.queryProfile(QueryUUIDsFilter.unmaskUUID(uuid), withSignature);
			response.ifPresent(profile -> {
				profile.id = uuid;
				profile.name += QueryUUIDsFilter.NAME_SUFFIX;
			});
		} else {
			response = customClient.queryProfile(uuid, withSignature);
		}

		if (response.isPresent()) {
			return Optional.of(Response.newFixedLength(Status.OK, null, YggdrasilResponseBuilder.queryProfile(response.get(), withSignature)));
		} else {
			return Optional.of(Response.newFixedLength(Status.NO_CONTENT, null, null));
		}
	}

}
