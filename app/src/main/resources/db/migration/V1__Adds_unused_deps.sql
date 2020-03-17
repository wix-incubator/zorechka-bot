CREATE TABLE `unused_deps` (
  `github_repo` varchar(1000) NOT NULL,
  `build_target` varchar(1000) NOT NULL,
  `hash` varchar(256) NOT NULL,
  PRIMARY KEY (`github_repo`,`build_target`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;