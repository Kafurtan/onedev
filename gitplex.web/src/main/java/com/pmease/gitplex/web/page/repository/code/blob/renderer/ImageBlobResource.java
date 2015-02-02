package com.pmease.gitplex.web.page.repository.code.blob.renderer;

import java.awt.Dimension;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.persistence.EntityNotFoundException;

import org.apache.shiro.SecurityUtils;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.request.resource.DynamicImageResource;
import org.apache.wicket.util.time.Time;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.io.ByteSource;
import com.google.common.io.Files;
import com.pmease.gitplex.core.GitPlex;
import com.pmease.gitplex.core.manager.RepositoryManager;
import com.pmease.gitplex.core.model.Repository;
import com.pmease.gitplex.core.permission.ObjectPermission;
import com.pmease.gitplex.web.exception.AccessDeniedException;
import com.pmease.gitplex.web.page.account.AccountPage;
import com.pmease.gitplex.web.page.repository.RepositoryPage;
import com.pmease.gitplex.web.service.FileBlob;

public class ImageBlobResource extends DynamicImageResource {

	private static final long serialVersionUID = 1L;

	/**
	 * Transient image data so that image only needs to be generated once per VM
	 */
	private transient SoftReference<byte[]> imageData;

	@Override
	protected byte[] getImageData(Attributes attributes) {
		// get image data is always called in sync block
		return getImageBytes(attributes.getParameters());
	}

	private synchronized byte[] getImageBytes(PageParameters params) {
		byte[] data = null;
		if (imageData != null) {
			data = imageData.get();
		}
		
		if (data == null) {
			data = internalGetImageData(params);
			imageData = new SoftReference<byte[]>(data);
			setLastModifiedTime(Time.now());
		}
		
		return data;
	}
	
	// private byte[] imageData;
	private synchronized byte[] internalGetImageData(PageParameters params) {
		final String username = params.get(AccountPage.PARAM_USER).toString();
		final String repositoryName = params.get(RepositoryPage.PARAM_REPO).toString();
		final String revision = params.get(RepositoryPage.PARAM_REVISION).toString();
		final String path = params.get(RepositoryPage.PARAM_PATH).toString();

		Repository repository = GitPlex.getInstance(RepositoryManager.class).findBy(
				username, repositoryName);
		if (repository == null) {
			throw new EntityNotFoundException("Repository " + username + "/"
					+ repositoryName + " doesn't exist");
		}

		Preconditions.checkState(!Strings.isNullOrEmpty(revision));

		Preconditions.checkState(!Strings.isNullOrEmpty(path));

		if (!SecurityUtils.getSubject().isPermitted(
				ObjectPermission.ofRepositoryRead(repository))) {
			throw new AccessDeniedException("Permission denied to access "
					+ repository.getFQN() + " for user "
					+ SecurityUtils.getSubject());
		}

		FileBlob blob = FileBlob.of(repository, revision, path);
		return blob.getData();
	}

	public Dimension getImageDimension(PageParameters params) {
		String path = params.get(RepositoryPage.PARAM_PATH).toString();
		
		Preconditions.checkState(!Strings.isNullOrEmpty(path));
		byte[] data = getImageBytes(params);
		
		String suffix = Files.getFileExtension(path);
		Iterator<ImageReader> iter = ImageIO.getImageReadersBySuffix(suffix);

		if (iter.hasNext()) {
			ImageReader reader = iter.next();
			try (BufferedInputStream is = (BufferedInputStream) ByteSource.wrap(data).openBufferedStream()) {
				
				reader.setInput(data);
				ImageInputStream stream = new MemoryCacheImageInputStream(is);
				reader.setInput(stream);
				int width = reader.getWidth(reader.getMinIndex());
				int height = reader.getHeight(reader.getMinIndex());

				return new Dimension(width, height);
			} catch (IOException e) {
				throw Throwables.propagate(e);
			} finally {
				reader.dispose();
			}
		}

		throw new IllegalArgumentException("Invalid image " + path);
	}
}