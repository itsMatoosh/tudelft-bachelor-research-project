import simplejson as json
from sklearn import metrics
from sklearn import cluster
import numpy as np
import matplotlib.pyplot as plt
from sklearn.decomposition import PCA
from sklearn.metrics.cluster import rand_score
from sklearn.metrics.cluster import normalized_mutual_info_score
from sklearn.model_selection import train_test_split

# test vars
INCLUDE_TRANSITIVE = True
INCLUDE_SINGLE_REPO = True
CLUSTERING_ALGO = 'DBSCAN'
AFFINITY_METRIC = 'AND'


def get_all_dependencies(repos):
    """
    Gets a sorted unique list of dependencies for all the given repos.
    Only uses dependencies which occur in more than one repo.
    # TEST: including transitive deps
    # TEST: exluding transitive deps
    # TEST: including deps with 1 repo
    # TEST: exluding deps with 1 repo
    """

    # count repos for each dependency
    all_dependencies = {}
    for repo in repos:
        for dependency in repo['dependencies']:
            # get dependency info
            dependencyId = dependency['id']
            dependencyType = dependency['type']

            # check dependency id
            idSplit = dependencyId.split(":")
            if len(idSplit) != 2:
                continue

            # skip transitive
            if not INCLUDE_TRANSITIVE and dependencyType == "TRANSITIVE":
                continue

            # add to counts
            if dependencyId not in all_dependencies:
                all_dependencies[dependencyId] = 1
            else:
                all_dependencies[dependencyId] += 1

    # remove all dependencies which are used just once
    if not INCLUDE_SINGLE_REPO:
        useless_deps = []
        for key in all_dependencies.keys():
            val = all_dependencies[key]
            if val <= 1:
                useless_deps.append(key)
        for useless_dep in useless_deps:
            all_dependencies.pop(useless_dep)

    return sorted(list(all_dependencies.keys()))


def get_all_categories(repos):
    """
    Gets all used categories from the given list of repos.
    :param repos:
    :return:
    """
    all_categories = []
    for repo in repos:
        repoCategory = repo['category']
        all_categories.append(repoCategory)
    return list(set(all_categories))


def get_ground_truth_clustering(repos, categories):
    """
    Gets the ground truth clustering of the repos based on their category labels.
    """
    clusters = []
    for repo in repos:
        repoCategory = repo['category']
        cluster = categories.index(repoCategory)
        clusters.append(cluster)
    return np.array(clusters)


def vectorize_repos(repos, dependencies):
    """
    Creates a vector representation of each of the given repos.
    Each entry of the vector represents a dependency which the repo contains.
    """

    vectors = {}

    for repo in repos:
        # get repo info
        repoId = repo['id']

        # initialize vector to 0
        vector = {}
        for axis in dependencies:
            vector[axis] = 0

        # set existing dependencies as 1
        for dependency in repo['dependencies']:
            dependencyId = dependency['id']
            if dependencyId in vector:
                vector[dependencyId] = 1

        # add to vectors
        vectors[repoId] = list(vector.values())

    return vectors


def visualize_clusters(repos, vectors, num_clusters, clusters):
    """
    Visualizes the produces clusterings.
    """

    # pca for better visualization
    pca = PCA(n_components=3)
    pc1 = pca.fit_transform(np.array(list(vectors.train_values())))

    # plot
    u_labels = np.unique(clusters)
    fig = plt.figure(figsize=(12, 12))
    ax = fig.add_subplot(projection='3d')
    for i in u_labels:
        xs = pc1[clusters == i, 0]
        ys = pc1[clusters == i, 1]
        zs = pc1[clusters == i, 2]
        ax.scatter(xs, ys, zs)
        # plt.scatter(xs, ys, label=i)
        for j, (xi, yi, zi) in enumerate(zip(xs, ys, zs)):
            repo = repos[j]
            repoId = repo["id"]
            ax.text(xi, yi, zi, repoId, va='top', ha='center')

    plt.legend(list(map(lambda x: fr"Cluster {x}", range(num_clusters))))
    plt.title("Dependency Structure GitHub Repository Clustering")
    plt.suptitle("Minecraft Mods vs. Minecraft Plugins")
    plt.show()


def calculate_and_max_affinity(vectors):
    """
    Calculates AND affinity matrix of the given vectors.
    :param vectors: Vectors to calculate the affinity for.
    :return: AND affinity matrix.
    """
    similarity = np.zeros((len(vectors), len(vectors)))
    for i, vector_0 in enumerate(vectors):
        for j, vector_1 in enumerate(vectors):
            num_0 = np.count_nonzero(vector_0)
            num_1 = np.count_nonzero(vector_1)
            if num_0 == 0 or num_1 == 0:
                similarity[i, j] = 0
                continue

            # and similarity
            band = np.bitwise_and(vector_0, vector_1)
            count = np.count_nonzero(band)
            sim = count / max(num_0, num_1)
            similarity[i, j] = sim

    return similarity


def calculate_xor_affinity(vectors):
    """
    Calculates XOR affinity matrix of the given vectors.
    :param vectors: Vectors to calculate the affinity for.
    :return: XOR affinity matrix.
    """
    similarity = np.zeros((len(vectors), len(vectors)))
    for i, vector_0 in enumerate(vectors):
        for j, vector_1 in enumerate(vectors):
            # xor similarity
            bxor = np.bitwise_xor(vector_0, vector_1)
            sim = 1 - (np.count_nonzero(bxor) / len(bxor))
            similarity[i, j] = sim

    return similarity


def calculate_dist_affinity(vectors):
    """
    Calculates distance affinity matrix of the given vectors.
    :param vectors: Vectors to calculate the affinity for.
    :return: Distance affinity matrix.
    """
    similarity = np.zeros((len(vectors), len(vectors)))
    for i, vector_0 in enumerate(vectors):
        for j, vector_1 in enumerate(vectors):
            # distance similarity
            vec_0 = np.array(vector_0)
            vec_1 = np.array(vector_1)
            dist = np.linalg.norm(vec_1 - vec_0)
            sim = - dist
            similarity[i, j] = sim

    return similarity


def benchmark_similarity(affinity_matrix, clusters_true):
    """
    Benchmarks a similarity metric with the similarity difference factor.
    :param affinity_matrix: Affinity matrix calculated with a given similarity metric.
    :param clusters_true: Groung truth clusterings.
    """
    # for each category calculate average similarity
    print("=== BENCHMARKING SIMILARITY ===")
    num_true_clusters = np.max(clusters_true) + 1
    total_inner_similarity = 0
    total_outer_similarity = 0
    for label in range(num_true_clusters):
        mask_inter = clusters_true == label
        mask_outer = clusters_true != label
        inter_cluster_similarity = np.average(affinity_matrix[mask_inter][:, mask_inter])
        total_inner_similarity += inter_cluster_similarity
        outer_cluster_similarity = np.average(affinity_matrix[mask_inter][:, mask_outer])
        total_outer_similarity += outer_cluster_similarity
    avg_inner_similarity = total_inner_similarity / num_true_clusters
    avg_outer_similarity = total_outer_similarity / num_true_clusters
    ratio = avg_inner_similarity / avg_outer_similarity
    print(fr"  Inner similarity: {avg_inner_similarity}")
    print(fr"  Outer similarity: {avg_outer_similarity}")
    print(fr"  Inner/outer ratio: {ratio}")


def benchmark_clustering(repos, clusters_true, clusters_pred):
    """
    Benchmarks a clustering metric comparing the predicted clusters to the ground truth clusters.
    :param repos: A list of mined repositories.
    :param clusters_true: Ground-truth clusters.
    :param clusters_pred: Predicted clusters.
    """
    print("=== BENCHMARKING CLUSTERING ===")
    # get rand index
    rand_index = rand_score(clusters_true, clusters_pred)
    print(fr"  Rand index: {rand_index}")
    # get nmi
    mutual_information = normalized_mutual_info_score(clusters_true, clusters_pred)
    print(fr"  Normalized mutual information: {mutual_information}")

    # show the categories makeup of predicted clusters
    clustered_repos = {}
    cluster_categories = {}
    for i, repo in enumerate(repos):
        repoId = repo['id']
        repoCategory = repo['category']
        if i >= len(clusters_pred): continue

        label = clusters_pred[i]
        if label in clustered_repos:
            # append repo
            clustered_repos[label].append(repoId)

            # update category counts
            category_counts = cluster_categories[label]
            category_counts[repoCategory] = category_counts[repoCategory] + 1
            cluster_categories[label] = category_counts
        else:
            clustered_repos[label] = [repoId]

            # create category counts
            category_counts = {}
            for category in all_categories:
                category_counts[category] = 0
            category_counts[repoCategory] = category_counts[repoCategory] + 1
            cluster_categories[label] = category_counts

    # calculate accuracy
    print(fr"  Clusters composition")
    for label in range(np.max(clusters_pred) + 1):
        print(fr"  - Cluster {label}")
        repos = clustered_repos[label]
        counts = cluster_categories[label]
        for category in all_categories:
            count = counts[category]
            print(fr"    -> {category}: {count}/{len(repos)}")


# get mined.json
minedJsonFile = "../mined.json"
# minedJsonFile = "../mined-50plugins-50mods.json"
# minedJsonFile = "../mined-50plugins-50mods-50android.json"
with open(minedJsonFile, 'r') as f:
    # read mined.json
    repos = json.load(f)
    print(fr"Loaded {len(repos)} repos")

    # split into training set and test set
    TEST_SET_SIZE = 0.2  # ! set to 0 for clustering and similarity benchmarks
    if TEST_SET_SIZE == 0:
        train_set = repos
        test_set = []
    else:
        train_set, test_set = train_test_split(repos, test_size=TEST_SET_SIZE)

    # get list of all dependencies
    all_dependencies = get_all_dependencies(train_set)
    print(fr"Extracted {len(all_dependencies)} dependencies")

    # get list of all categories
    all_categories = get_all_categories(train_set)
    print(fr"Extracted {len(all_categories)} categories")

    # get ground truth clusterings
    train_clusters_true = get_ground_truth_clustering(train_set, all_categories)

    # convert each repo to a vector of dependencies
    train_vectors = vectorize_repos(train_set, all_dependencies)

    # calculate similarity matrix
    train_values = np.array(list(train_vectors.values()))
    if AFFINITY_METRIC == 'AND':
        affinity_matrix = calculate_and_max_affinity(train_values)
    elif AFFINITY_METRIC == 'XOR':
        affinity_matrix = calculate_xor_affinity(train_values)
    elif AFFINITY_METRIC == 'DIST':
        affinity_matrix = calculate_dist_affinity(train_values)

    # evaluate similarity metric
    benchmark_similarity(affinity_matrix, train_clusters_true)

    # set number of clusters
    num_clusters = 3
    print(fr"Number of clusters: {num_clusters}")

    # cluster
    if CLUSTERING_ALGO == 'KMEANS':
        # apply kmeans
        k_means = cluster.KMeans(n_clusters=num_clusters)
        clusters_pred = k_means.fit_predict(train_values)
    elif CLUSTERING_ALGO == "AGLO":
        # apply aglo
        aglo = cluster.AgglomerativeClustering(n_clusters=num_clusters, affinity='precomputed', linkage='average',
                                               compute_full_tree=True)
        clusters_pred = aglo.fit_predict(affinity_matrix)
    elif CLUSTERING_ALGO == "DBSCAN":
        hdb = cluster.HDBSCAN(metric='precomputed',
                              min_cluster_size=5,
                              cluster_selection_epsilon=0.5,
                              store_centers='centroid', )
        clusters_pred = hdb.fit_predict(1 - affinity_matrix)
    elif CLUSTERING_ALGO == "SPECTRAL":
        spectral = cluster.SpectralClustering(n_clusters=num_clusters, affinity='precomputed')
        clusters_pred = spectral.fit_predict(affinity_matrix)

    # benchmark clusters
    benchmark_clustering(train_set, train_clusters_true, clusters_pred)

    # get centoids
    centroids = []
    for i in range(np.max(clusters_pred)):
        # collect all vectors in cluster
        s = 0
        accum = np.zeros(train_values.shape[1])
        for j, c in enumerate(clusters_pred):
            if c != i: continue
            s += 1
            vector = train_values[j]
            accum += vector
        centroid = accum / s
        centroids.append(centroid)
    centroids = np.array(centroids)

    # hybrid approach
    test_vectors = vectorize_repos(test_set, all_dependencies)
    test_values = np.array(list(test_vectors.values()))
    test_characteristic_vectors = []
    for i in range(len(test_values)):
        test_value = test_values[i]
        test_characteristic_vector = np.zeros(len(centroids))
        for j, centroid in enumerate(centroids):
            dist = np.linalg.norm(test_value - centroid)
            test_characteristic_vector[j] = dist
        test_characteristic_vectors.append(test_characteristic_vector)
    test_characteristic_vectors = np.array(test_characteristic_vectors)

    # get DIST similarities in hybrid approach
    hybrid_affinity_matrix = calculate_dist_affinity(test_characteristic_vectors)

    # get ground truth clusterings
    test_clusters_true = get_ground_truth_clustering(test_set, all_categories)

    # benchmark hybrid metric
    benchmark_similarity(hybrid_affinity_matrix, test_clusters_true)
