# Discovering Digital Siblings: Quantifying Inter-Repository Similarity Through GitHub Dependency Structures ![DOI Badge](https://zenodo.org/badge/DOI/10.5281/zenodo.10576708.svg)
This project is part of the [Research Project 2024](https://github.com/TU-Delft-CSE/Research-Project) of [TU Delft](https://https//github.com/TU-Delft-CSE).

Open Source developers typically use Git repositories to transparently store the source code of projects and contribute to the code of others. There are millions of repositories actively hosted on platforms such as GitHub. This presents an opportunity for sharing knowledge between related projects. Finding repositories similar to one's own can allow for better developer collaboration and knowledge transfer. However, due to the large volume of projects, manually locating related repositories can be difficult. Hence, this paper proposes a novel approach, based on the dependency structures of a project, that allows for calculating inter-repository similarity and subsequently querying for similar projects. We aim to answer the research question: How can the dependency structures of GitHub repositories be leveraged to calculate their similarity to other repositories? This research includes an empirical evaluation of various similarity metrics and clustering techniques for GitHub repositories. Our results show that dependency structures are a reliable characteristic for measuring similarity between projects. We also identify the specific metrics and clustering techniques as particularly efficient. Lastly, we propose and evaluate a composable similarity metric to allow our findings to be combined with the research of the other Research Project group members.

## Running Dependency Extraction
The list of repositories to extract the dependency information from is located at `/run/repos.txt`.
- First, build the project using `./gradlew jar`.
- Then, run the compiled JAR using `java -jar git-dependency-miner.jar --file "./run/repos.txt"`.
- The extracted dependency information will be written to `mined.json`.

## Running Analysis
- Ensure that the `mined.json` file is available in the root directory of this repository.
- Navigate into the `analysis/` directory by using `cd analysis/`.
- Ensure that all Python dependencies are installed by running `pip3 install -r "./requirements.txt"`.
- Execute the analysis code using Python by running `python3 analysis.py`. 
